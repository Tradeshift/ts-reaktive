package com.tradeshift.reaktive.aws



import com.amazonaws.auth.InstanceProfileCredentialsProvider
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.autoscaling.model.{DescribeAutoScalingGroupsRequest, DescribeAutoScalingInstancesRequest}
import com.amazonaws.services.autoscaling.{AmazonAutoScaling, AmazonAutoScalingClientBuilder}
import com.amazonaws.services.ec2.model.InstanceStateName.Running
import com.amazonaws.services.ec2.model.{DescribeInstancesRequest, Instance}
import com.amazonaws.services.ec2.{AmazonEC2, AmazonEC2ClientBuilder}
import com.amazonaws.util.EC2MetadataUtils
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._
import scala.collection.immutable



private[aws] object AWSClient {

  def apply(): AWSClient = apply(                                       // NOTEST no AWS integration in tests 
    EC2MetadataUtils.getInstanceId,
    InstanceProfileCredentialsProvider.getInstance,
    Regions.getCurrentRegion
  )

  def apply(thisInstanceId: String,
            credentialsProvider: InstanceProfileCredentialsProvider,
            region: Region): AWSClient =
    new AWSClient(thisInstanceId,                                       // NOTEST no AWS integration in tests
      AmazonEC2ClientBuilder.standard()                                 // NOTEST no AWS integration in tests
        .withCredentials(credentialsProvider)                           // NOTEST no AWS integration in tests
        .withRegion(region.getName)                                     // NOTEST no AWS integration in tests
        .build(),                                                       // NOTEST no AWS integration in tests
      AmazonAutoScalingClientBuilder.standard()                         // NOTEST no AWS integration in tests
        .withCredentials(credentialsProvider)                           // NOTEST no AWS integration in tests
        .withRegion(region.getName)                                     // NOTEST no AWS integration in tests
        .build()                                                        // NOTEST no AWS integration in tests
    )
}

private[aws] class AWSClient(thisInstanceId: String, amazonEC2: AmazonEC2, amazonAutoScaling: AmazonAutoScaling) {
  val log: Logger = LoggerFactory.getLogger(getClass.getName)

  /**
    * Returns a list of private IPs of running instances, which make good candidates for akka seed nodes.
    *
    * Instances are taken from the same auto-scaling group as the current running instance. 
    * All IPs in a list are sorted by instance launch date. A list
    * can contain the private IP of current running instance if it is the first launched instance,
    * otherwise it is excluded. 
    */
  def candidateSeedNodes: immutable.Seq[Instance] = {
    val groupsMembers = getAutoScalingGroupInstanceIds(autoScalingGroupName)
      .map(loadInstanceDetails)
      .filter(_.getState.getName == Running.toString)
      .sortBy(_.getInstanceId)

    if (groupsMembers.nonEmpty && thisInstanceId == groupsMembers.head.getInstanceId) {
      // The first node is allowed to join itself as seed and start the cluster
      groupsMembers
    } else {
      // Non-first nodes must join another before starting
      groupsMembers.filter(_.getInstanceId != thisInstanceId)
    }
  }

  def autoScalingGroupName: String = {
    val request = new DescribeAutoScalingInstancesRequest().withInstanceIds(thisInstanceId)

    val autoScalingInstances = amazonAutoScaling
      .describeAutoScalingInstances(request)
      .getAutoScalingInstances

    log.debug("Auto Scaling Instances {}", autoScalingInstances)
    if (autoScalingInstances.size != 1) {
      throw new IllegalStateException("Instance doesn't belong to single auto-scaling group")
    }
    autoScalingInstances.get(0).getAutoScalingGroupName
  }

  private def loadInstanceDetails(instanceId: String): Instance = {
    val reservations = amazonEC2.describeInstances(new DescribeInstancesRequest().withInstanceIds(instanceId))
      .getReservations

    log.debug("Reservations {}", reservations)

    reservations.get(0).getInstances.get(0)
  }

  private def getAutoScalingGroupInstanceIds(groupName: String): immutable.Seq[String] = {
    val request = new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(groupName)

    val autoScalingGroups = amazonAutoScaling
      .describeAutoScalingGroups(request)
      .getAutoScalingGroups

    log.debug("Auto Scaling Groups {}", autoScalingGroups)

    autoScalingGroups.get(0).getInstances.asScala.toVector.map(_.getInstanceId)
  }
}
