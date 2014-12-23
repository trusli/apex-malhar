/*
 * Copyright (c) 2014 DataTorrent, Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datatorrent.contrib.couchbase;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datatorrent.lib.helper.OperatorContextTestHelper;
import com.datatorrent.lib.testbench.CollectorTestSink;

import com.datatorrent.api.Attribute.AttributeMap;
import com.datatorrent.api.DAG;
import com.datatorrent.api.DefaultPartition;
import com.datatorrent.api.Partitioner.Partition;

import com.datatorrent.common.util.DTThrowable;
import com.datatorrent.lib.io.fs.AbstractFSDirectoryInputOperator;
import com.datatorrent.lib.io.fs.AbstractFSDirectoryInputOperatorTest.TestFSDirectoryInputOperator;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.Test;

public class CouchBaseInputOperatorTest
{
  private static final Logger logger = LoggerFactory.getLogger(CouchBaseInputOperatorTest.class);
  private static String APP_ID = "CouchBaseInputOperatorTest";
  private static String bucket = "default";
  private static String password = "";
  private static int OPERATOR_ID = 0;
  protected static ArrayList<URI> nodes = new ArrayList<URI>();
  protected static ArrayList<String> keyList;
  private static String uri = "node13.morado.com:8091,node14.morado.com:8091";

  
  public void TestCouchBaseInputOperator()
  {
    CouchBaseWindowStore store = new CouchBaseWindowStore();
    keyList = new ArrayList<String>();
    store.setBucket(bucket);
    store.setPassword(password);
    store.setUriString(uri);
    try {
      store.connect();
    }
    catch (IOException ex) {
      DTThrowable.rethrow(ex);
    }

    store.getInstance().flush();
    AttributeMap.DefaultAttributeMap attributeMap = new AttributeMap.DefaultAttributeMap();
    attributeMap.put(DAG.APPLICATION_ID, APP_ID);
    OperatorContextTestHelper.TestIdOperatorContext context = new OperatorContextTestHelper.TestIdOperatorContext(OPERATOR_ID, attributeMap);

    TestInputOperator inputOperator = new TestInputOperator();
    inputOperator.setStore(store);
    inputOperator.insertEventsInTable(100);

    CollectorTestSink<Object> sink = new CollectorTestSink<Object>();
    inputOperator.outputPort.setSink(sink);
    inputOperator.setup(context);
    inputOperator.beginWindow(0);
    inputOperator.emitTuples();
    inputOperator.endWindow();

    Assert.assertEquals("tuples in couchbase", 100, sink.collectedTuples.size());
  }

  @Test
  public void TestCouchBaseInputOperatorWithPartitions()
  {
    CouchBaseWindowStore store = new CouchBaseWindowStore();
    keyList = new ArrayList<String>();
    store.setBucket(bucket);
    store.setPassword(password);
    store.setUriString(uri);
    try {
      store.connect();
    }
    catch (IOException ex) {
      DTThrowable.rethrow(ex);
    }

    store.getInstance().flush();
    //store.getInstance().flush();
     AttributeMap.DefaultAttributeMap attributeMap = new AttributeMap.DefaultAttributeMap();
    attributeMap.put(DAG.APPLICATION_ID, APP_ID);
    OperatorContextTestHelper.TestIdOperatorContext context = new OperatorContextTestHelper.TestIdOperatorContext(OPERATOR_ID, attributeMap);
    List<Partition<AbstractCouchBaseInputOperator<String>>> partitions = Lists.newArrayList();
    TestInputOperator inputOperator = new TestInputOperator();
    inputOperator.setStore(store);
    inputOperator.insertEventsInTable(100);
    CollectorTestSink<Object> sink = new CollectorTestSink<Object>();
    inputOperator.outputPort.setSink(sink);
    partitions.add(new DefaultPartition<AbstractCouchBaseInputOperator<String>>(inputOperator));
    Collection<Partition<AbstractCouchBaseInputOperator<String>>> newPartitions = inputOperator.definePartitions(partitions, 1);
    Assert.assertEquals(2, newPartitions.size());
     for (Partition<AbstractCouchBaseInputOperator<String>> p : newPartitions) {
      Assert.assertNotSame(inputOperator, p.getPartitionedInstance());
     }
      /* Collect all operators in a list */
    List<AbstractCouchBaseInputOperator<String>> opers = Lists.newArrayList();
    for (Partition<AbstractCouchBaseInputOperator<String>> p : newPartitions) {
      TestInputOperator oi = (TestInputOperator)p.getPartitionedInstance();
      oi.setStore(store);
      oi.setup(null);
      oi.outputPort.setSink(sink);
      opers.add(oi);
    }

    sink.clear();
    int wid = 0;
    for(int i = 0; i < 10; i++) {
    //for(int i = 0; i < 10; i++) {
      for(AbstractCouchBaseInputOperator<String> o : opers) {
        o.beginWindow(wid);
        o.emitTuples();
        o.endWindow();
      }
      wid++;
    }
     // wid++;
    //}

    Assert.assertEquals("Tuples read should be same ", 100, sink.collectedTuples.size());
    }


  public static class TestInputOperator extends AbstractCouchBaseInputOperator<String>
  {

    @SuppressWarnings("unchecked")
    @Override
    public String getTuple(Object entry)
    {
      String tuple = entry.toString();
      return tuple;
    }

    @Override
    public ArrayList<String> getKeys()
    {
      return keyList;
    }

    public void insertEventsInTable(int numEvents)
    {
      String key = null;
      Integer value = null;
      logger.info("number of events is" + numEvents);
      for (int i = 0; i < numEvents; i++) {
        key = String.valueOf("Key" + i * 10);
        keyList.add(key);
        value = i * 100;
        try {
          store.client.set(key, value).get();
        }
        catch (InterruptedException ex) {
          DTThrowable.rethrow(ex);
        }
        catch (ExecutionException ex) {
          DTThrowable.rethrow(ex);
        }
      }
    }

  }

}

