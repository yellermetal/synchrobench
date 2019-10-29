package transactionLib;

import java.util.Iterator;

public interface RangeIterator<E> extends Iterator<E> {

	void init();
	
	void init_from(E start);
	
	void init_upTo(E end);
	
	void init_range(E start, E end);
}
