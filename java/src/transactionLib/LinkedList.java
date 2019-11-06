package transactionLib;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

/**
 * Interface of a Skiplist
 * Based of https://github.com/HagarMeir/transactionLib
 * 
 * @author Hagar Meir and Ariel Livshits
 * 
 */

public class LinkedList implements Iterable<Object> {

    private static Unsafe unsafe = null;

    static {
        Field f = null;
        try {
            f = Unsafe.class.getDeclaredField("theUnsafe");
        } catch (NoSuchFieldException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        f.setAccessible(true);
        try {
            unsafe = (Unsafe) f.get(null);
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    protected LNode head = new LNode(Integer.MIN_VALUE, null); // protected (not private) for testing
    protected Index index;

    public LinkedList() {
        // TODO(GG) the comparator/index is a nested class, its code explicitly
        // ensures that head is the minimal element
        index = new Index(head);
    }

    // for debug only
    protected void printLinkedListNotInTX() {
        int size = 0;
        LNode n = head;
        while (n != null) {
            size++;
            System.out.print(n.key + "--");
            n = n.next;
        }
        System.out.print("\n");
        System.out.println(size + " in list");
    }

    // for debug only
    private void printWriteSet() {
        HashMap<LNode, WriteElement> ws = TX.lStorage.get().writeSet;
        for (Entry<LNode, WriteElement> entry : ws.entrySet()) {
            LNode node = entry.getKey();
            WriteElement we = entry.getValue();
            if (we.next != null) {
                System.out.print(node.key + "->" + we.next.key + " , ");
            }
        }
        System.out.print("\n");
    }

    private LNode getPredSingleton(LNode n) {
        LNode pred = index.getPred(n);
        while (pred.isLockedOrDeleted()) {
            if (pred == head) {
                return head;
            }
            pred = index.getPred(pred);
        }
        return pred;
    }

    private Object getVal(LNode n, LocalStorage localStorage) { 	
        WriteElement we = localStorage.writeSet.get(n);
        if (we != null) {
            return we.val;
        }
        return n.val;
    }

    private LNode getPred(LNode n, LocalStorage localStorage) throws TXLibExceptions.AbortException {
        LNode pred = index.getPred(n);
        while (true) {
            if (pred.isLocked() || pred.getVersion() > localStorage.readVersion) {
                // abort TX
                localStorage.TX = false;
                TXLibExceptions excep = new TXLibExceptions();
                throw excep.new AbortException();
            }
            if (pred.isSameVersionAndSingleton(localStorage.readVersion)) {
                // TODO in the case of a thread running singleton and then TX
                // this TX will abort once but for no reason
                TX.incrementAndGetVersion();
                localStorage.TX = false;
                TXLibExceptions excep = new TXLibExceptions();
                throw excep.new AbortException();
            }
            WriteElement we = localStorage.writeSet.get(pred);
            if (we != null) {
                if (we.deleted) {
                    // if you deleted it earlier
                    assert (pred != head);
                    pred = index.getPred(pred);
                    continue;
                }
            }
            if (pred.isDeleted()) {
                assert (pred != head);
                pred = index.getPred(pred);
            } else {
                return pred;
            }
        }
    }

    private LNode getNext(LNode n, LocalStorage localStorage) throws TXLibExceptions.AbortException {
        // first try to read from private write set
        WriteElement we = localStorage.writeSet.get(n);
        if (we != null) {
            return we.next;
        }

        // because we don't read next and locked at once,
        // we first see if locked, then read next and then re-check locked
        if (n.isLocked()) {
            // abort TX
            localStorage.TX = false;
            TXLibExceptions excep = new TXLibExceptions();            
            throw excep.new AbortException();
        }
        unsafe.loadFence();
        LNode next = n.next;
        unsafe.loadFence();
        if (n.isLocked() || n.getVersion() > localStorage.readVersion) {
            // abort TX
            localStorage.TX = false;
            TXLibExceptions excep = new TXLibExceptions();
            throw excep.new AbortException();
        }
        if (n.isSameVersionAndSingleton(localStorage.readVersion)) {
            TX.incrementAndGetVersion();
            localStorage.TX = false;
            TXLibExceptions excep = new TXLibExceptions();
            throw excep.new AbortException();
        }
        return next;
    }

    private Object putSingleton(Integer key, Object val) {

        LNode n = new LNode(key, val);

        LNode pred;
        LNode next;

        while (true) {

            boolean startOver = false;

            pred = getPredSingleton(n);
            if (pred.isLocked()) {
                continue;
            }
            unsafe.loadFence();
            next = pred.next; // TODO maybe this is not necessary
            unsafe.loadFence();
            if (pred.isLockedOrDeleted()) {
                continue;
            }

            while (next != null) {

                if (next.isLockedOrDeleted()) {
                    // when we encounter a locked node while traversing the list
                    // we have to start over
                    startOver = true;
                    break;
                }

                if (next.key.compareTo(key) == 0) {
                    // the key exists, change to new value
                    LNode node = pred.next;
                    if (node.tryLock()) {

                        if (node.key.compareTo(key) != 0 || node != next || node.isDeleted()) {
                            node.unlock();
                            startOver = true;
                            break;
                        }
                        Object ret = node.val;
                        node.val = val;
                        node.setSingleton(true);
                        node.setVersion(TX.getVersion());
                        node.unlock();
                        return ret; // return previous value associated with key
                    } else {
                        startOver = true;
                        break;
                    }
                } else if (next.key.compareTo(key) > 0) {
                    // key doesn't exist, perform insert
                    if (pred.tryLock()) {

                        if (pred.isDeleted() || next != pred.next) {
                            pred.unlock();
                            startOver = true;
                            break;
                        }

                        n.next = pred.next;
                        pred.next = n;
                        n.setVersionAndSingletonNoLockAssert(TX.getVersion(), true);
                        pred.unlock();
                        index.add(n);
                        return null;
                    } else {
                        startOver = true;
                        break;
                    }
                }
                // next is still strictly less than key
                if (next.isLocked() || next != pred.next) {
                    startOver = true;
                    break;
                }
                unsafe.loadFence();
                pred = pred.next;
                if (pred == null) {
                    // next was not null but now perd.next is null
                    // to prevent null exception later
                    startOver = true;
                    break;
                }
                next = pred.next;
                unsafe.loadFence();
                if (pred.isLockedOrDeleted()) {
                    startOver = true;
                    break;
                }
            }

            if (startOver) {
                continue;
            }

            // all are strictly less than key
            // put at end
            if (pred.tryLock()) {

                if (pred.isDeleted() || pred.next != null) {
                    pred.unlock();
                    continue;
                }

                pred.next = n;
                pred.unlock();
                index.add(n);
                return null;
            }

        }

    }

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
    public Object put(Integer key, Object val) throws TXLibExceptions.AbortException {

        if (key == null || val == null)
            throw new NullPointerException();

        // Get transaction local storage (write-set, read-set)
        LocalStorage localStorage = TX.lStorage.get();

        // if called by a singleton
        if (!localStorage.TX) {
            return putSingleton(key, val);
        }

        localStorage.readOnly = false;

        LNode node = new LNode(key, val);

        LNode pred = getPred(node, localStorage);
        LNode next = getNext(pred, localStorage);
        boolean found = false;

        while (next != null) {
            if (next.key.compareTo(key) == 0) {
                found = true;
                break; 
            } 
            else if (next.key.compareTo(key) < 0) {
                pred = next;
                next = getNext(pred, localStorage);
            } 
            else
            	break;
        }

        if (found) {
            WriteElement we = localStorage.writeSet.get(next);
            if (we != null) {
                // if it is already in write set then just change val
                localStorage.putIntoWriteSet(next, we.next, val, we.deleted);
            } 
            else {
                localStorage.putIntoWriteSet(next, next.next, val, false);
            }
            // add to read set
            localStorage.readSet.add(next);
            if (TX.DEBUG_MODE_LL) {
                System.out.println("put key " + key + ":");
                printWriteSet();
            }
            return next.val;
        }

        // not found
        node.next = next;
        localStorage.putIntoWriteSet(pred, node, getVal(pred, localStorage), false);
        localStorage.addToIndexAdd(this, node);

        // add to read set
        localStorage.readSet.add(pred);

        if (TX.DEBUG_MODE_LL) {
            System.out.println("put key " + key + ":");
            printWriteSet();
        }

        return null;
    }

    private Object putIfAbsentSingleton(Integer key, Object val) {

        LNode n = new LNode(key, val);

        LNode pred;
        LNode next;

        while (true) {

            boolean startOver = false;

            pred = getPredSingleton(n);
            if (pred.isLocked()) {
                continue;
            }
            unsafe.loadFence();
            next = pred.next; // TODO maybe this is not necessary
            unsafe.loadFence();
            if (pred.isLockedOrDeleted()) {
                continue;
            }

            while (next != null) {

                if (next.isLockedOrDeleted()) {
                    // when we encounter a locked node while traversing the list
                    // we have to start over
                    startOver = true;
                    break;
                }

                if (next.key.compareTo(key) == 0) {
                    // the key exists, return value
                    LNode node = pred.next;
                    if (node.key.compareTo(key) != 0 || node != next || node.isLockedOrDeleted()) {
//						node.unlock();
                        startOver = true;
                        break;
                    }
                    // return previous value associated with key
                    return node.val;
                } else if (next.key.compareTo(key) > 0) {
                    // key doesn't exist, perform insert
                    if (pred.tryLock()) {

                        if (pred.isDeleted() || next != pred.next) {
                            pred.unlock();
                            startOver = true;
                            break;
                        }

                        n.next = pred.next;
                        pred.next = n;
                        n.setVersionAndSingletonNoLockAssert(TX.getVersion(), true);
                        pred.unlock();
                        index.add(n);
                        return null;
                    } else {
                        startOver = true;
                        break;
                    }
                }
                // next is still strictly less than key
                if (next.isLocked() || next != pred.next) {
                    startOver = true;
                    break;
                }
                unsafe.loadFence();
                pred = pred.next;
                if (pred == null) {
                    // next was not null but now perd.next is null
                    // to prevent null exception later
                    startOver = true;
                    break;
                }
                next = pred.next;
                unsafe.loadFence();
                if (pred.isLockedOrDeleted()) {
                    startOver = true;
                    break;
                }
            }

            if (startOver) {
                continue;
            }

            // all are strictly less than key
            // put at end
            if (pred.tryLock()) {
                if (pred.isDeleted() || pred.next != null) {
                    pred.unlock();
                    continue;
                }

                pred.next = n;
                pred.unlock();
                index.add(n);
                return null;
            }

        }

    }

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
    public Object putIfAbsent(Integer key, Object val) throws TXLibExceptions.AbortException {

        if (key == null || val == null)
            throw new NullPointerException();

        LocalStorage localStorage = TX.lStorage.get();

        // SINGLETON
        if (!localStorage.TX) {
            return putIfAbsentSingleton(key, val);
        }

        // TX
        localStorage.readOnly = false;

        LNode n = new LNode(key, val);

        LNode pred = getPred(n, localStorage);
        LNode next = getNext(pred, localStorage);
        boolean found = false;

        while (next != null) {
            if (next.key.compareTo(key) == 0) {
                found = true;
                break;
            } else if (next.key.compareTo(key) > 0) {
                break;
            } else {
                pred = next;
                next = getNext(pred, localStorage);
            }
        }

        if (found) {
            // the key exists, return value
            localStorage.readSet.add(next); // add to read set
            return next.val;
        }

        // not found
        n.next = next;
        localStorage.putIntoWriteSet(pred, n, getVal(pred, localStorage), false);
        localStorage.addToIndexAdd(this, n);
        localStorage.readSet.add(pred); // add to read set

        return null;
    }

    private Object removeSingleton(Integer key) {

        LNode n = new LNode(key, null);

        LNode pred;
        LNode next;

        while (true) {

            boolean startOver = false;

            pred = getPredSingleton(n);
            if (pred.isLocked()) {
                continue;
            }
            unsafe.loadFence();
            next = pred.next;
            unsafe.loadFence();
            if (pred.isLockedOrDeleted()) {
                continue;
            }

            while (next != null) {

                if (next.isLockedOrDeleted()) {
                    // when we encounter a locked node while traversing the list
                    // we have to start over
                    startOver = true;
                    break;
                }

                if (next.key.compareTo(key) < 0) {
                    if (next.isLocked() || next != pred.next) {
                        startOver = true;
                        break;
                    }
                    unsafe.loadFence();
                    pred = pred.next;
                    if (pred == null) {
                        // next was not null but now perd.next is null
                        // to prevent null exception later
                        startOver = true;
                        break;
                    }
                    next = pred.next;
                    unsafe.loadFence();
                    if (pred.isLockedOrDeleted()) {
                        startOver = true;
                        break;
                    }
                } else if (next.key.compareTo(key) > 0) {
                    if (next != pred.next) {
                        startOver = true;
                        break;
                    }
                    // key does not exist
                    return null;
                } else {
                    // the key exists
                    if (TX.DEBUG_MODE_LL) {
                        System.out.println("removeSingleton: the key exists " + key);
                    }
                    if (pred.tryLock()) {
                        if (TX.DEBUG_MODE_LL) {
                            System.out.println("removeSingleton: pred was locked of key " + key);
                        }
                        if (pred.isDeleted() || next != pred.next) {
                            pred.unlock();
                            startOver = true;
                            break;
                        }
                        LNode toRemove;
                        Object valToRet;
                        if (next.tryLock()) {
                            toRemove = next;
                            valToRet = toRemove.val;
                            toRemove.val = null; // for Index
                            pred.next = pred.next.next;
                            long ver = TX.getVersion();
                            toRemove.setVersionAndDeletedAndSingleton(ver, true, true);
                            pred.setVersionAndSingleton(ver, true);
                            if (TX.DEBUG_MODE_LL) {
                                System.out.println("removeSingleton: removed key " + key);
                            }
                        } else {
                            pred.unlock();
                            startOver = true;
                            break;
                        }
                        toRemove.unlock();
                        pred.unlock();
                        index.remove(toRemove);
                        return valToRet;
                    } else {
                        if (TX.DEBUG_MODE_LL) {
                            System.out.println("removeSingleton: the key exists, couldn't lock " + key);
                        }
                        startOver = true;
                        break;
                    }
                }

            }

            if (startOver) {
                continue;
            }

            return null;
        }

    }
    
    /**
     * @effects Removes the mapping for a key from this map if it is present
     * 
     * @return  The value to which this map previously associated the key,
     * 		    or null if the map contained no mapping for the key.
     * 
     * @throws  NullPointerException if the specified key is null
     */
    public Object remove(Integer key) throws TXLibExceptions.AbortException {

        if (key == null)
            throw new NullPointerException();

        LocalStorage localStorage = TX.lStorage.get();

        // SINGLETON
        if (!localStorage.TX) {
            return removeSingleton(key);
        }

        // TX

        localStorage.readOnly = false;

        LNode node = new LNode(key, null);

        LNode pred = getPred(node, localStorage);
        LNode next = getNext(pred, localStorage);
        boolean found = false;

        while (next != null) {
            if (next.key.compareTo(key) == 0) {
                found = true;
                break;
            } else if (next.key.compareTo(key) > 0) {
                break;
            } else {
                pred = next;
                next = getNext(pred, localStorage);
            }
        }

        if (found) {
            localStorage.putIntoWriteSet(pred, getNext(next, localStorage), getVal(pred, localStorage), false);
            localStorage.putIntoWriteSet(next, null, getVal(next, localStorage), true);
            // add to read set
            localStorage.readSet.add(next);
            localStorage.addToIndexRemove(this, next);
        }

        // add to read set
        localStorage.readSet.add(pred);

        if (!found) {
            return null;
        }
        WriteElement we = localStorage.writeSet.get(next);
        if (we != null) {
            return we.val;
        }
        return next.val;
    }

    private boolean containsKeySingleton(Integer key) {

        LNode n = new LNode(key, null);

        LNode pred = null;
        LNode next;

        boolean startOver = true;

        while (true) {

            if (startOver) {
                pred = getPredSingleton(n);
            } else {
                pred = pred.next;
                if (pred == null) {
                    // next was not null but now perd.next is null
                    // to prevent null exception later
                    startOver = true;
                    continue;
                }
            }

            startOver = false;

            if (pred.isLocked()) {
                startOver = true;
                continue;
            }
            unsafe.loadFence();
            next = pred.next;
            unsafe.loadFence();
            if (pred.isLockedOrDeleted()) {
                startOver = true;
                continue;
            }

            if (next == null || next.key.compareTo(key) > 0) {
                // key does not exist
                return false;
            } else if (next.key.compareTo(key) == 0) {
                return true;
            } else {
                assert (next.key.compareTo(key) < 0);
                if (next != pred.next) {
                    startOver = true;
                }
            }

        }

    }

    /**
     * @return  true, if key is present. false, if it is not.
     * 
     * @throws  NullPointerException if the specified key is null
     */
    public boolean containsKey(Integer key) throws TXLibExceptions.AbortException {

        if (key == null)
            throw new NullPointerException();

        LocalStorage localStorage = TX.lStorage.get();

        // SINGLETON
        if (!localStorage.TX) {
            return containsKeySingleton(key);
        }

        // TX

        LNode n = new LNode(key, null);

        LNode pred = getPred(n, localStorage);
        LNode next = getNext(pred, localStorage);

        while (next != null && next.key.compareTo(key) < 0) {
            pred = next;
            next = getNext(pred, localStorage);
        }

        // add to read set
        localStorage.readSet.add(pred);

        if (next == null || next.key.compareTo(key) > 0) {
            return false;
        } else {
            assert (next.key.compareTo(key) == 0);
            return true;
        }

    }

    private Object getSingleton(Integer key) {

        if (key == null)
            throw new NullPointerException();

        LNode n = new LNode(key, null);

        LNode pred = null;
        LNode next;

        boolean startOver = true;

        while (true) {

            if (startOver) {
                pred = getPredSingleton(n);
            } else {
                pred = pred.next;
                if (pred == null) {
                    // next was not null but now perd.next is null
                    // to prevent null exception later
                    startOver = true;
                    continue;
                }
            }

            startOver = false;

            if (pred.isLocked()) {
                startOver = true;
                continue;
            }
            unsafe.loadFence();
            next = pred.next;
            unsafe.loadFence();
            if (pred.isLockedOrDeleted()) {
                startOver = true;
                continue;
            }

            if (next == null || next.key.compareTo(key) > 0) {
                // key does not exist
                return null;
            } else if (next.key.compareTo(key) == 0) {
                return next.val;
            } else {
                assert (next.key.compareTo(key) < 0);
                if (next != pred.next) {
                    startOver = true;
                }
            }

        }

    }

    /**
     * @return  The value associated with key, or null if key not present.
     * 
     * @throws  NullPointerException if the specified key is null
     */
    public Object get(Integer key) throws TXLibExceptions.AbortException {

    	if (key == null)
            throw new NullPointerException();
    	
        LocalStorage localStorage = TX.lStorage.get();

        // SINGLETON
        if (!localStorage.TX) {
            return getSingleton(key);
        }

        // TX

        LNode pred = getPred(new LNode(key, null), localStorage);
        LNode next = getNext(pred, localStorage);

        while (next != null && next.key.compareTo(key) < 0) {
            pred = next;
            next = getNext(pred, localStorage);
        }

        if (TX.DEBUG_MODE_LL) {
            System.out.println("get key " + key + ":");
            System.out.println("pred is " + pred.key);
            printWriteSet();
        }

        // add to read set
        localStorage.readSet.add(pred);

        if (next == null || next.key.compareTo(key) > 0) {
            return null;
        } else {
            assert (next.key.compareTo(key) == 0);
            return getVal(next, localStorage);
        }
    }


    private RangeIterator<Object> iteratorSingleton() {
    	
    	return new RangeIterator<Object>() {

    		private LNode node = head;
    		private Integer end = Integer.MAX_VALUE;
    		
    		private LNode getNext(LNode pred) {

            	LNode next;
                while (true) {

                    if (pred.isLocked())
                        continue;
                   
                    unsafe.loadFence();
                    next = pred.next;
                    unsafe.loadFence();

                    if (pred.isLocked() || (next != null && next.isDeleted()))
                        continue;

                    return next;
                }
            }
    		
    		@Override
			public void init() {
				node = head;				
			}

			@Override
			public void init_from(Object start) {
				node = getPredSingleton(new LNode((int) start, null));			
			}

			@Override
			public void init_upTo(Object end) {
				node = head;
				this.end = (int) end;
				
			}

			@Override
			public void init_range(Object start, Object end) {
				
				this.init_from(start);
				this.end = (int) end;
				
			}
    		
			@Override
			public boolean hasNext() {
				
				LNode next = this.getNext(node);
				if (next == null || next.key > end)
    	    		return false;

    	        return true;
			}

			@Override
			public Object next() {
				node = this.getNext(node);
				if (node == null)
    	        	throw new NoSuchElementException();
            	return node.val;
			}
    		
    	};
    }
    
    /**
     * @return  A RangeIterator Object which supports range queries. 
     */
	public RangeIterator<Object> iterator()  throws TXLibExceptions.AbortException {
		
		LocalStorage localStorage = TX.lStorage.get();

        // SINGLETON
        if (!localStorage.TX) {
            return iteratorSingleton();
        }
        // TX
        return new RangeIterator<Object>() {

    		private LNode node;
    		private Integer end = Integer.MAX_VALUE;
    	    private LocalStorage localStorage;
    	    
    	    @Override
    		public void init() {
    	    	
    	    	node = head;
    	    	localStorage = TX.lStorage.get();
    	    	localStorage.readSet.add(node);
    			
    		}
    	
    		@Override
    		public void init_from(Object start) {

    			localStorage = TX.lStorage.get();
    			node = getPred(new LNode((int) start, null), localStorage);			    	
    	    	localStorage.readSet.add(node);
    			
    		}
    		
    		@Override
    		public void init_upTo(Object end) {

    			node = head;
    			localStorage = TX.lStorage.get();		    	
    	    	localStorage.readSet.add(node);
    	    	this.end = (int) end;
    			
    		}
    	
    		@Override
    		public void init_range(Object start, Object end) {
    			
    			this.init_from(start);
    			this.end = (int) end;
    						
    		}
    	    
    	    @Override
    	    public boolean hasNext() {
        	    
    	    	LNode next = getNext(node, localStorage);
    	    	if  (next == null || next.key > end)
    	        	return false;
    	    	
    	        return true;
    	    }
    	
    	    @Override
    	    public Object next() {
    	   			          	
    	        node = getNext(node, localStorage);
    	        
    	        if (node == null)
    	        	throw new NoSuchElementException();
    	        
    	        localStorage.readSet.add(node);
    	    	return getVal(node, localStorage);
    	    }

        };
	}

	
	public int getSize() throws TXLibExceptions.AbortException {
		
		int counter = 0;
		
		RangeIterator<Object> iter = iterator();
		iter.init();
		while(iter.hasNext()) {
			counter++;
			iter.next();
		}
		
		return counter;
		
	}

}
