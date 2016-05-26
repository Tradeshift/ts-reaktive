ts-reaktive-kamon-akka-http
---------------------------

This module sends akka http server metrics to kamon. You activate the module by having it on the classpath together with kamon. 

All akka http servers are then instrumented automatically, under `akka-http-server.<port>`.
