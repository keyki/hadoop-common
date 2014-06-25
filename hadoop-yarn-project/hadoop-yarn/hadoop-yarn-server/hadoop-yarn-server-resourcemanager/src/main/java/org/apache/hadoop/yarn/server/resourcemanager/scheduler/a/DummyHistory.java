package org.apache.hadoop.yarn.server.resourcemanager.scheduler.a;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.hadoop.yarn.api.records.NodeId;

public class DummyHistory {

  public static Queue<NodeId> assignments = new ConcurrentLinkedQueue<NodeId>();
  public static boolean finished = false;

}
