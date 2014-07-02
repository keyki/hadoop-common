/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.server.resourcemanager.scheduler.a;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.net.NetworkTopology;
import org.apache.hadoop.yarn.LocalConfigurationProvider;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.ResourceRequest;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.event.AsyncDispatcher;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.server.resourcemanager.Application;
import org.apache.hadoop.yarn.server.resourcemanager.NodeManager;
import org.apache.hadoop.yarn.server.resourcemanager.RMContext;
import org.apache.hadoop.yarn.server.resourcemanager.ResourceManager;
import org.apache.hadoop.yarn.server.resourcemanager.Task;
import org.apache.hadoop.yarn.server.resourcemanager.rmnode.RMNode;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.ResourceScheduler;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.capacity.CapacitySchedulerConfiguration;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.event.NodeAddedSchedulerEvent;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.event.NodeUpdateSchedulerEvent;
import org.apache.hadoop.yarn.util.resource.Resources;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import junit.framework.Assert;

public class TestExtendedCapacitySchedulerAppMove {
  private static final Log LOG = LogFactory.getLog(TestExtendedCapacityScheduler.class);
  private final int GB = 1024;

  private static final String A = CapacitySchedulerConfiguration.ROOT + ".a";
  private static final String B = CapacitySchedulerConfiguration.ROOT + ".b";
  private static final String A1 = A + ".a1";
  private static final String A2 = A + ".a2";
  private static final String B1 = B + ".b1";
  private static final String B2 = B + ".b2";
  private static final String B3 = B + ".b3";
  private static float A_CAPACITY = 10.5f;
  private static float B_CAPACITY = 89.5f;
  private static float A1_CAPACITY = 30;
  private static float A2_CAPACITY = 70;
  private static float B1_CAPACITY = 79.2f;
  private static float B2_CAPACITY = 0.8f;
  private static float B3_CAPACITY = 20;

  private ResourceManager resourceManager = null;
  private RMContext mockContext;

  @Before
  public void setUp() throws Exception {
    resourceManager = new ResourceManager();
    CapacitySchedulerConfiguration csConf = new CapacitySchedulerConfiguration();
    setupQueueConfiguration(csConf);
    YarnConfiguration conf = new YarnConfiguration(csConf);
    conf.setClass(YarnConfiguration.RM_SCHEDULER, ExtendedCapacityScheduler.class, ResourceScheduler.class);
    resourceManager.init(conf);
    resourceManager.getRMContext().getContainerTokenSecretManager().rollMasterKey();
    resourceManager.getRMContext().getNMTokenSecretManager().rollMasterKey();
    ((AsyncDispatcher)resourceManager.getRMContext().getDispatcher()).start();
    mockContext = mock(RMContext.class);
    when(mockContext.getConfigurationProvider()).thenReturn(new LocalConfigurationProvider());
  }

  @After
  public void tearDown() throws Exception {
    resourceManager.stop();
  }


  private NodeManager registerNode(String hostName, int containerManagerPort, int httpPort, String rackName, Resource capability) throws IOException, YarnException {
    NodeManager nm = new NodeManager(hostName, containerManagerPort, httpPort, rackName, capability, resourceManager);
    NodeAddedSchedulerEvent nodeAddEvent1 = new NodeAddedSchedulerEvent(resourceManager.getRMContext().getRMNodes().get(nm.getNodeId()));
    resourceManager.getResourceScheduler().handle(nodeAddEvent1);
    return nm;
  }

  @Test(expected = YarnException.class)
  public void testMoveAppViolateMaxCapacity() throws Exception {

    // Register node1
    String host_0 = "host_0";
    NodeManager nm_0 = registerNode(host_0, 1234, 2345, NetworkTopology.DEFAULT_RACK, Resources.createResource(3 * GB, 1));

    // Register node2
    String host_1 = "host_1";
    NodeManager nm_1 = registerNode(host_1, 1234, 2345, NetworkTopology.DEFAULT_RACK, Resources.createResource(3 * GB, 1));

    // ResourceRequest priorities
    Priority priority_0 = org.apache.hadoop.yarn.server.resourcemanager.resource.Priority.create(0);
    Priority priority_1 = org.apache.hadoop.yarn.server.resourcemanager.resource.Priority.create(1);

    // Submit application_0
    Application application_0 = new Application("user_0", "a1", resourceManager);
    application_0.submit(); // app + app attempt event sent to scheduler

    application_0.addNodeManager(host_0, 1234, nm_0);
    application_0.addNodeManager(host_1, 1234, nm_1);

    Resource capability_0_0 = Resources.createResource(3 * GB, 1);
    application_0.addResourceRequestSpec(priority_1, capability_0_0);

    Resource capability_0_1 = Resources.createResource(2 * GB, 1);
    application_0.addResourceRequestSpec(priority_0, capability_0_1);

    Task task_0_0 = new Task(application_0, priority_1, new String[] {host_0, host_1});
    application_0.addTask(task_0_0);

    // Submit application_1
    Application application_1 = new Application("user_1", "b2", resourceManager);
    application_1.submit(); // app + app attempt event sent to scheduler

    application_1.addNodeManager(host_0, 1234, nm_0);
    application_1.addNodeManager(host_1, 1234, nm_1);

    Resource capability_1_0 = Resources.createResource(3 * GB, 1);
    application_1.addResourceRequestSpec(priority_1, capability_1_0);

    Resource capability_1_1 = Resources.createResource(2 * GB, 1);
    application_1.addResourceRequestSpec(priority_0, capability_1_1);

    Task task_1_0 = new Task(application_1, priority_1, new String[] {host_0, host_1});
    application_1.addTask(task_1_0);

    // Send resource requests to the scheduler
    application_0.schedule(); // allocate
    application_1.schedule(); // allocate

    // task_0_0 allocated
    nodeUpdate(nm_0);

    nodeUpdate(nm_1);

    // Get allocations from the scheduler
    application_0.schedule();     // task_0_0
    checkApplicationResourceUsage(3 * GB, application_0);

    application_1.schedule();     // task_1_0
    checkApplicationResourceUsage(3 * GB, application_1);

    checkNodeResourceUsage(3*GB, nm_0);
    checkNodeResourceUsage(3*GB, nm_1);

    // b2 queue contains 3GB consumption app, add another 3GB will hit max capacity limit on queue b
    application_0.moveToQueue("b1");
  }

  @Test
  public void testMoveAppForMoveToQueueCannotRunApp() throws Exception {

    // Register node1
    String host_0 = "host_0";
    NodeManager nm_0 = registerNode(host_0, 1234, 2345, NetworkTopology.DEFAULT_RACK, Resources.createResource(4 * GB, 1));

    // Register node2
    String host_1 = "host_1";
    NodeManager nm_1 = registerNode(host_1, 1234, 2345, NetworkTopology.DEFAULT_RACK, Resources.createResource(2 * GB, 1));

    // ResourceRequest priorities
    Priority priority_0 = org.apache.hadoop.yarn.server.resourcemanager.resource.Priority.create(0);
    Priority priority_1 = org.apache.hadoop.yarn.server.resourcemanager.resource.Priority.create(1);

    // Submit application_0
    Application application_0 = new Application("user_0", "a1", resourceManager);
    application_0.submit(); // app + app attempt event sent to scheduler

    application_0.addNodeManager(host_0, 1234, nm_0);
    application_0.addNodeManager(host_1, 1234, nm_1);

    Resource capability_0_0 = Resources.createResource(1 * GB, 1);
    application_0.addResourceRequestSpec(priority_1, capability_0_0);

    Resource capability_0_1 = Resources.createResource(2 * GB, 1);
    application_0.addResourceRequestSpec(priority_0, capability_0_1);

    Task task_0_0 = new Task(application_0, priority_1, new String[] {host_0, host_1});
    application_0.addTask(task_0_0);

    // Submit application_1
    Application application_1 = new Application("user_1", "b2", resourceManager);
    application_1.submit(); // app + app attempt event sent to scheduler

    application_1.addNodeManager(host_0, 1234, nm_0);
    application_1.addNodeManager(host_1, 1234, nm_1);

    Resource capability_1_0 = Resources.createResource(1 * GB, 1);
    application_1.addResourceRequestSpec(priority_1, capability_1_0);

    Resource capability_1_1 = Resources.createResource(2 * GB, 1);
    application_1.addResourceRequestSpec(priority_0, capability_1_1);

    Task task_1_0 = new Task(application_1, priority_1, new String[] {host_0, host_1});
    application_1.addTask(task_1_0);

    // Send resource requests to the scheduler
    application_0.schedule(); // allocate
    application_1.schedule(); // allocate

    // task_0_0 task_1_0 allocated, used=4G
    nodeUpdate(nm_0);

    // nothing allocated
    nodeUpdate(nm_1);

    // Get allocations from the scheduler
    application_0.schedule();     // task_0_0
    checkApplicationResourceUsage(1 * GB, application_0);

    application_1.schedule();     // task_1_0
    checkApplicationResourceUsage(1 * GB, application_1);

    checkNodeResourceUsage(2*GB, nm_0);  // task_0_0 (1G) and task_1_0 (1G) 2G available
    checkNodeResourceUsage(0*GB, nm_1);  // no tasks, 2G available

    // move app from a1(30% cap of total 10.5% cap) to b1(79,2% cap of 89,5% total cap)
    application_0.moveToQueue("b1");

    // 2GB 1C
    Task task_1_1 = new Task(application_1, priority_0, new String[] {ResourceRequest.ANY});
    application_1.addTask(task_1_1);

    application_1.schedule();

    // 2GB 1C
    Task task_0_1 = new Task(application_0, priority_0, new String[] {host_0, host_1});
    application_0.addTask(task_0_1);

    application_0.schedule();

    // prev 2G used free 2G
    nodeUpdate(nm_0);

    //prev 0G used free 2G
    nodeUpdate(nm_1);

    // Get allocations from the scheduler
    application_1.schedule();
    checkApplicationResourceUsage(3 * GB, application_1);

    // Get allocations from the scheduler
    application_0.schedule();
    checkApplicationResourceUsage(3 * GB, application_0);

    checkNodeResourceUsage(4*GB, nm_0);
    checkNodeResourceUsage(2*GB, nm_1);
  }

  @Test
  public void testMoveApp() throws Exception {

    // Register node1
    String host_0 = "host_0";
    NodeManager nm_0 = registerNode(host_0, 1234, 2345, NetworkTopology.DEFAULT_RACK, Resources.createResource(5 * GB, 1));

    // Register node2
    String host_1 = "host_1";
    NodeManager nm_1 = registerNode(host_1, 1234, 2345, NetworkTopology.DEFAULT_RACK, Resources.createResource(5 * GB, 1));

    // ResourceRequest priorities
    Priority priority_0 = org.apache.hadoop.yarn.server.resourcemanager.resource.Priority.create(0);
    Priority priority_1 = org.apache.hadoop.yarn.server.resourcemanager.resource.Priority.create(1);

    // Submit application_0
    Application application_0 = new Application("user_0", "a1", resourceManager);
    application_0.submit(); // app + app attempt event sent to scheduler

    application_0.addNodeManager(host_0, 1234, nm_0);
    application_0.addNodeManager(host_1, 1234, nm_1);

    Resource capability_0_0 = Resources.createResource(3 * GB, 1);
    application_0.addResourceRequestSpec(priority_1, capability_0_0);

    Resource capability_0_1 = Resources.createResource(2 * GB, 1);
    application_0.addResourceRequestSpec(priority_0, capability_0_1);

    Task task_0_0 = new Task(application_0, priority_1, new String[] {host_0, host_1});
    application_0.addTask(task_0_0);

    // Submit application_1
    Application application_1 = new Application("user_1", "b2", resourceManager);
    application_1.submit(); // app + app attempt event sent to scheduler

    application_1.addNodeManager(host_0, 1234, nm_0);
    application_1.addNodeManager(host_1, 1234, nm_1);

    Resource capability_1_0 = Resources.createResource(1 * GB, 1);
    application_1.addResourceRequestSpec(priority_1, capability_1_0);

    Resource capability_1_1 = Resources.createResource(2 * GB, 1);
    application_1.addResourceRequestSpec(priority_0, capability_1_1);

    Task task_1_0 = new Task(application_1, priority_1, new String[] {host_0, host_1});
    application_1.addTask(task_1_0);

    // Send resource requests to the scheduler
    application_0.schedule(); // allocate
    application_1.schedule(); // allocate

    // b2 can only run 1 app at a time
    application_0.moveToQueue("b2");

    nodeUpdate(nm_0);

    nodeUpdate(nm_1);

    // Get allocations from the scheduler
    application_0.schedule();     // task_0_0
    checkApplicationResourceUsage(0 * GB, application_0);

    application_1.schedule();     // task_1_0
    checkApplicationResourceUsage(1 * GB, application_1);

    checkNodeResourceUsage(1*GB, nm_0);  // task_1_0 (1G) application_0 moved to b2 with max running app 1 so it is not scheduled
    checkNodeResourceUsage(0*GB, nm_1);

    // lets move application_0 to a queue where it can run
    application_0.moveToQueue("a2");
    application_0.schedule();

    nodeUpdate(nm_1);

    // Get allocations from the scheduler
    application_0.schedule();     // task_0_0
    checkApplicationResourceUsage(3 * GB, application_0);

    checkNodeResourceUsage(1*GB, nm_0);
    checkNodeResourceUsage(3*GB, nm_1);

  }

  private void nodeUpdate(NodeManager nm) {
    RMNode node = resourceManager.getRMContext().getRMNodes().get(nm.getNodeId());
    // Send a heartbeat to kick the tires on the Scheduler
    NodeUpdateSchedulerEvent nodeUpdate = new NodeUpdateSchedulerEvent(node);
    resourceManager.getResourceScheduler().handle(nodeUpdate);
  }

  private void setupQueueConfiguration(CapacitySchedulerConfiguration conf) {

    // Define top-level queues
    conf.setQueues(CapacitySchedulerConfiguration.ROOT, new String[] {"a", "b"});

    conf.setCapacity(A, A_CAPACITY);
    conf.setCapacity(B, B_CAPACITY);

    // Define 2nd-level queues
    conf.setQueues(A, new String[] {"a1", "a2"});
    conf.setCapacity(A1, A1_CAPACITY);
    conf.setUserLimitFactor(A1, 100.0f);
    conf.setCapacity(A2, A2_CAPACITY);
    conf.setUserLimitFactor(A2, 100.0f);

    conf.setQueues(B, new String[] {"b1", "b2", "b3"});
    conf.setCapacity(B1, B1_CAPACITY);
    conf.setUserLimitFactor(B1, 100.0f);
    conf.setCapacity(B2, B2_CAPACITY);
    conf.setUserLimitFactor(B2, 100.0f);
    conf.setCapacity(B3, B3_CAPACITY);
    conf.setUserLimitFactor(B3, 100.0f);

    LOG.info("Setup top-level queues a and b");
  }

  private void checkApplicationResourceUsage(int expected, Application application) {
    Assert.assertEquals(expected, application.getUsedResources().getMemory());
  }

  private void checkNodeResourceUsage(int expected,
                                      NodeManager node) {
    Assert.assertEquals(expected, node.getUsed().getMemory());
    node.checkResourceUsage();
  }
}
