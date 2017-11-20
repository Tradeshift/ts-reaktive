package com.tradeshift.reaktive.aws

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model._
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.InstanceStateName.{Running, Stopping}
import com.amazonaws.services.ec2.model.{Instance => EC2Instance, _}
import org.mockito.Mockito.{mock, reset, when}
import org.scalatest.{Matchers, WordSpec}

import scala.collection.JavaConverters._
import scala.collection.immutable

class AWSClientSpec extends WordSpec with Matchers {

  private val amazonEC2 = mock(classOf[AmazonEC2])
  private val amazonAutoScaling = mock(classOf[AmazonAutoScaling])

  private val defaultEC2Instances = immutable.Seq[EC2Instance](
    new EC2Instance().withInstanceId("i-0a1add1122ba90c74").withState(new InstanceState().withName(Stopping.toString)),
    new EC2Instance().withInstanceId("i-0a1add1122ba90c76").withState(new InstanceState().withName(Running.toString)),
    new EC2Instance().withInstanceId("i-0a1add1122ba90c75").withState(new InstanceState().withName(Running.toString)),
    new EC2Instance().withInstanceId("i-0a1add1122ba90c77").withState(new InstanceState().withName(Running.toString))
  )

  def mockAutoScalingInstances(instanceId: String): Unit = {
    mockAutoScalingInstances(instanceId,
      new DescribeAutoScalingInstancesResult().withAutoScalingInstances(
        new AutoScalingInstanceDetails().withInstanceId(instanceId).withAutoScalingGroupName("identity-core-auto-scaling-group")
      )
    )
  }

  def mockAutoScalingInstances(instanceId: String,
                               autoScalingInstancesResult: DescribeAutoScalingInstancesResult): Unit = {
    when(amazonAutoScaling.describeAutoScalingInstances(
      new DescribeAutoScalingInstancesRequest().withInstanceIds(instanceId)
    )).thenReturn(autoScalingInstancesResult)
  }

  def mockInstances(instance: EC2Instance): Unit = {
    when(amazonEC2.describeInstances(
      new DescribeInstancesRequest().withInstanceIds(instance.getInstanceId))
    ).thenReturn(
      new DescribeInstancesResult().withReservations(
        immutable.Seq(new Reservation().withInstances(immutable.Seq(instance).asJava)).asJava
      )
    )
  }

  def mockAutoScalingGroupsResult(asgName: String, instances: immutable.Seq[EC2Instance]): Unit = {
    when(amazonAutoScaling.describeAutoScalingGroups(
      new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(asgName)
    )).thenReturn(
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups(
        new AutoScalingGroup().withInstances(
          instances.map(i => new Instance().withInstanceId(i.getInstanceId)).asJava
        )
      )
    )
  }

  "AWSClient" when {
    reset(amazonEC2, amazonAutoScaling)

    "candidateSeedNodes" should {

      "return IP addresses of running instances" when {

        "including own address if it is first in sorted by InstanceId collection" in {
          mockAutoScalingGroupsResult("identity-core-auto-scaling-group", defaultEC2Instances)

          defaultEC2Instances.foreach(mockInstances)
          defaultEC2Instances.map(_.getInstanceId).foreach(mockAutoScalingInstances)

          val client = new AWSClient("i-0a1add1122ba90c75", amazonEC2, amazonAutoScaling)

          client.candidateSeedNodes.size shouldBe 3
        }

        "excluding own address if it is not first in sorted by InstanceId collection" in {
          mockAutoScalingGroupsResult("identity-core-auto-scaling-group", defaultEC2Instances)

          defaultEC2Instances.foreach(mockInstances)
          defaultEC2Instances.map(_.getInstanceId).foreach(mockAutoScalingInstances)

          val client = new AWSClient("i-0a1add1122ba90c76", amazonEC2, amazonAutoScaling)

          client.candidateSeedNodes.size shouldBe 2
        }
      }

      "return empty list of IP addresses if there are no instances in Auto Scaling Group" in {
        mockAutoScalingGroupsResult("identity-core-auto-scaling-group", immutable.Seq[EC2Instance]())

        defaultEC2Instances.foreach(mockInstances)
        defaultEC2Instances.map(_.getInstanceId).foreach(mockAutoScalingInstances)

        val client = new AWSClient("i-0a1add1122ba90c75", amazonEC2, amazonAutoScaling)

        client.candidateSeedNodes.size shouldBe 0
      }
    }

    "getAutoScalingGroupName" should {

      "throw an exception if instance for a given InstanceId" when {

        "belongs to more than 1 Auto Scaling Group" in {
          val describeAutoScalingInstancesResult = new DescribeAutoScalingInstancesResult().withAutoScalingInstances(
            new AutoScalingInstanceDetails().withInstanceId("i-0a5add7393ba90c75"),
            new AutoScalingInstanceDetails().withInstanceId("i-0a5add7393ba90c75")
          )

          mockAutoScalingInstances("i-0a1add1122ba90c75", describeAutoScalingInstancesResult)

          val client = new AWSClient("i-0a1add1122ba90c75", amazonEC2, amazonAutoScaling)
          assertThrows[IllegalStateException] {
            client.autoScalingGroupName
          }
        }

        "does not belong to any Auto Scaling Group" in {
          mockAutoScalingInstances("i-0a1add1122ba90c75", new DescribeAutoScalingInstancesResult())

          val client = new AWSClient("i-0a1add1122ba90c75", amazonEC2, amazonAutoScaling)
          assertThrows[IllegalStateException] {
            client.autoScalingGroupName
          }
        }
      }
    }
  }
}
