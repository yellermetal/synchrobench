package structures;

import transactionLib.RangeIterator;

/**
 * Interface of a Skiplist
 * 
 * @author Ariel Livshits
 * 
 */

public interface Skiplist<K, V> {

	/** 
     * @effects Associates the specified value with the specified key in this map.
	 * 			If the map previously contained a mapping for the key, the old value is replaced.
	 * 
	 * @return  the previous value associated with key, or null if there was no
	 * 			mapping for key. (A null return can also indicate that the map previously
	 * 			associated null with key, if the implementation supports null values.)
	 * 
	 * @throws  NullPointerException if the specified key or value is null
	 */
	public V put(K key, V val);
	
	/**  
     * @effects If the specified key is not already associated with a value,
     * 			associate it with the given value.
     * 
     * @return  The previous value associated with the specified key,
     * 			or null if there was no mapping for the key.
     * 			(A null return can also indicate that the map previously associated
     * 			null with the key, if the implementation supports null values.)
     * 
     * @throws  NullPointerException if the specified key or value is null
     */
	public V putIfAbsent(K key, V val);
	
	/**
     * @effects Removes the mapping for a key from this map if it is present
     * 
     * @return  The value to which this map previously associated the key,
     * 		    or null if the map contained no mapping for the key.
     * 
     * @throws  NullPointerException if the specified key is null
     */
	public V remove(K key);
	
	/**
     * @return  true, if key is present. false, if it is not.
     * 
     * @throws  NullPointerException if the specified key is null
     */
	public boolean containsKey(K key);
	
	/**
     * @return  The value associated with key, or null if key not present.
     * 
     * @throws  NullPointerException if the specified key is null
     */
	public V get(K key);
	
	/**
     * @return  A RangeIterator Object which supports range queries. 
     */
	public RangeIterator<V> iterator(boolean atomic);
	
}
