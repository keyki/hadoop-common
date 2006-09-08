/*
 * Copyright 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.io.compress;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

import junit.framework.TestCase;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.conf.Configuration;

public class TestCodecFactory extends TestCase {

  private static class BaseCodec implements CompressionCodec {
    public CompressionOutputStream createOutputStream(OutputStream out) {
      return null;
    }
    
    public CompressionInputStream createInputStream(InputStream in) {
      return null;
    }
    
    public String getDefaultExtension() {
      return ".base";
    }
  }
  
  private static class BarCodec extends BaseCodec {
    public String getDefaultExtension() {
      return "bar";
    }
  }
  
  private static class FooBarCodec extends BaseCodec {
    public String getDefaultExtension() {
      return ".foo.bar";
    }
  }
  
  private static class FooCodec extends BaseCodec {
    public String getDefaultExtension() {
      return ".foo";
    }
  }
  
  /**
   * Returns a factory for a given set of codecs
   * @param classes the codec classes to include
   * @return a new factory
   */
  private static CompressionCodecFactory setClasses(Class[] classes) {
    Configuration conf = new Configuration();
    CompressionCodecFactory.setCodecClasses(conf, Arrays.asList(classes));
    return new CompressionCodecFactory(conf);
  }
  
  private static void checkCodec(String msg, 
                                 Class expected, CompressionCodec actual) {
    assertEquals(msg + " unexpected codec found",
                 expected.getName(),
                 actual.getClass().getName());
  }
  
  public static void testFinding() {
    CompressionCodecFactory factory = 
      new CompressionCodecFactory(new Configuration());
    CompressionCodec codec = factory.getCodec(new Path("/tmp/foo.bar"));
    assertEquals("default factory foo codec", null, codec);
    codec = factory.getCodec(new Path("/tmp/foo.gz"));
    checkCodec("default factory for .gz", GzipCodec.class, codec);
    factory = setClasses(new Class[0]);
    codec = factory.getCodec(new Path("/tmp/foo.bar"));
    assertEquals("empty codec bar codec", null, codec);
    codec = factory.getCodec(new Path("/tmp/foo.gz"));
    assertEquals("empty codec gz codec", null, codec);
    factory = setClasses(new Class[]{BarCodec.class, FooCodec.class, 
                                     FooBarCodec.class});
    codec = factory.getCodec(new Path("/tmp/.foo.bar.gz"));
    assertEquals("full factory gz codec", null, codec);
    codec = factory.getCodec(new Path("/tmp/foo.bar"));
    checkCodec("full factory bar codec", BarCodec.class, codec);
    codec = factory.getCodec(new Path("/tmp/foo/baz.foo.bar"));
    checkCodec("full factory foo bar codec", FooBarCodec.class, codec);
    codec = factory.getCodec(new Path("/tmp/foo.foo"));
    checkCodec("full factory foo codec", FooCodec.class, codec);
  }
}
