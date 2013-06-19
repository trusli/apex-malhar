/*
 *  Copyright (c) 2012 Malhar, Inc.
 *  All Rights Reserved.
 */
package com.datatorrent.lib.math;

import com.datatorrent.api.DefaultInputPort;
import com.datatorrent.api.DefaultOutputPort;
import com.datatorrent.api.StreamCodec;
import com.datatorrent.api.annotation.InputPortFieldAnnotation;
import com.datatorrent.api.annotation.OutputPortFieldAnnotation;
import com.datatorrent.lib.util.BaseNumberKeyValueOperator;
import com.datatorrent.lib.util.KeyValPair;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * Emits at end of window maximum of all values sub-classed from Number for each key in KeyValPair. <p>
 * <br>
 * <b>Ports</b>:<br>
 * <b>data</b>: expects KeyValPair&lt;K,V extends Number&gt;<br>
 * <b>max</b>: emits KeyValPair&lt;K,V extends Number&gt;, one entry per key<br>
 * <br>
 * <b>Properties</b>:<br>
 * <b>inverse</b>: if set to true the key in the filter will block tuple<br>
 * <b>filterBy</b>: List of keys to filter on<br>
 * <br>
 * <b>Specific compile time checks</b>: None<br>
 * <b>Specific run time checks</b>: None<br>
 * <p>
 * <b>Benchmarks</b>: Blast as many tuples as possible in inline mode<br>
 * <table border="1" cellspacing=1 cellpadding=1 summary="Benchmark table for MaxMap&lt;K,V extends Number&gt; operator template">
 * <tr><th>In-Bound</th><th>Out-bound</th><th>Comments</th></tr>
 * <tr><td><b>35 Million K,V pairs/s</b></td><td>One tuple per key per window per port</td><td>In-bound rate is the main determinant of performance. Tuples are assumed to be
 * immutable. If you use mutable tuples and have lots of keys, the benchmarks may be lower.</td></tr>
 * </table><br>
 * <p>
 * <b>Function Table (K=String, V=Integer)</b>:
 * <table border="1" cellspacing=1 cellpadding=1 summary="Function table for MaxMap&lt;K,V extends Number&gt; operator template">
 * <tr><th rowspan=2>Tuple Type (api)</th><th>In-bound (<i>data</i>::process)</th><th>Out-bound (emit)</th></tr>
 * <tr><th><i>data</i>(KeyValPair&lt;K,V&gt;)</th><th><i>max</i>(KeyValPair&lt;K,V&gt;)</th></tr>
 * <tr><td>Begin Window (beginWindow())</td><td>N/A</td><td>N/A</td></tr>
 * <tr><td>Data (process())</td><td>a=2</td><td></td></tr>
 * <tr><td>Data (process())</td><td>b=20</td><td></td></tr>
 * <tr><td>Data (process())</td><td>c=1000</td><td></td></tr>
 * <tr><td>Data (process())</td><td>a=1</td><td></td></tr>
 * <tr><td>Data (process())</td><td>a=10</td><td></td></tr>
 * <tr><td>Data (process())</td><td>b=5</td><td></td></tr>
 * <tr><td>Data (process())</td><td>d=55</td><td></td></tr>
 * <tr><td>Data (process())</td><td>b=12</td><td></td></tr>
 * <tr><td>Data (process())</td><td>d=22</td><td></td></tr>
 * <tr><td>Data (process())</td><td>d=14</td><td></td></tr>
 * <tr><td>Data (process())</td><td>d=46</td><td></td></tr>
 * <tr><td>Data (process())</td><td>e=2</td><td></td></tr>
 * <tr><td>Data (process())</td><td>d=4</td><td></td></tr>
 * <tr><td>Data (process())</td><td>a=23</td><td></td></tr>
 * <tr><td>End Window (endWindow())</td><td>N/A</td><td>a=10<br>b=20<br>c=1000<br>d=55<br>e=2</td></tr>
 * </table>
 * <br>
 *
 * <br>
 */
public class MaxKeyVal<K, V extends Number> extends BaseNumberKeyValueOperator<K, V>
{
  @InputPortFieldAnnotation(name = "data")
  public final transient DefaultInputPort<KeyValPair<K, V>> data = new DefaultInputPort<KeyValPair<K, V>>(this)
  {
    /**
     * For each key, updates the hash if the new value is a new max.
     */
    @Override
    public void process(KeyValPair<K, V> tuple)
    {
      K key = tuple.getKey();
      V tval = tuple.getValue();
      if (!doprocessKey(key) || (tval == null)) {
        return;
      }
      V val = highs.get(key);
      if (val == null) {
        val = tval;
        highs.put(cloneKey(key), val);
      }
      else if (val.doubleValue() < tval.doubleValue()) {
        highs.put(key, tval);
      }
    }

    /**
     * Set StreamCodec used for partitioning.
     */
    @Override
    public Class<? extends StreamCodec<KeyValPair<K, V>>> getStreamCodec()
    {
      return getKeyValPairStreamCodec();
    }
  };
  @OutputPortFieldAnnotation(name = "max")
  public final transient DefaultOutputPort<KeyValPair<K, V>> max = new DefaultOutputPort<KeyValPair<K, V>>(this);

  protected HashMap<K, V> highs = new HashMap<K, V>();

  /**
   * Emits all key,max value pairs.
   * Override getValue() if you have your own class extended from Number.
   * Clears internal data. Node only works in windowed mode.
   */
  @Override
  public void endWindow()
  {
    if (!highs.isEmpty()) {
      for (Map.Entry<K, V> e: highs.entrySet()) {
        max.emit(new KeyValPair(e.getKey(), e.getValue()));
      }
      clearCache();
    }
  }

  public void clearCache()
  {
    highs.clear();
  }
}
