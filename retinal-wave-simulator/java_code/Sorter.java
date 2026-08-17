
class Sorter {
	static void sortDoubleAsc(double data[], int len)
	{
		int i;
//		for (i=0; i<len; i++)
//			System.out.println("" + data[i]);
//		System.out.println("-------------");

		int len2 = 1;
		// find lowest power of 2 >= len
		while (len2 < len)
			len2 *= 2;
		double buf[] = new double[len2];
		double buf2[] = new double[len2];
		for (i=0; i<len; i++)
			buf[i] = data[i];
		for (; i<len2; i++)
			buf[i] = Double.MAX_VALUE;
		int chunkSize = 1;
		while (chunkSize < len2)
		{
//			System.out.println("Chunksize = " + chunkSize);
//			for (i=0; i<len; i++)
//				System.out.println("\t" + buf[i]);

			int pos = 0;
			int writePos = 0;
			while (pos < len2)
			{
//System.out.println("Pos: "+pos);
				int left = 0;
				int right = 0;
				while ((left < chunkSize) && (right < chunkSize))
				{
					int leftPos = left + pos;
					int rightPos = right + pos + chunkSize;
					if (buf[leftPos] < buf[rightPos])
					{
//System.out.println(" buf2["+writePos+"] = buf["+leftPos+"](="+buf[leftPos]+")  - left("+leftPos+") copy");
						buf2[writePos] = buf[leftPos];
						left++;
					}
					else
					{
//System.out.println(" buf2["+writePos+"] = buf["+rightPos+"](="+buf[rightPos]+")  - right("+rightPos+") copy");
						buf2[writePos] = buf[rightPos];
						right++;
					}
					writePos++;
				}
				while (left < chunkSize)
				{
					int leftPos = left + pos;
//System.out.println(" buf2["+writePos+"] = buf["+leftPos+"](="+buf[leftPos]+")");
					buf2[writePos] = buf[leftPos];
					left++;
					writePos++;
				}
				while (right < chunkSize)
				{
					int rightPos = right + pos + chunkSize;
//System.out.println(" buf2["+writePos+"] = buf["+rightPos+"](="+buf[rightPos]+")");
					buf2[writePos] = buf[rightPos];
					right++;
					writePos++;
				}
				pos += 2*chunkSize;
			}
			chunkSize *= 2;
			// swap buf pointers
			double tmp[] = buf;
			buf = buf2;
			buf2 = tmp;
		}
		for (i=0; i<len; i++)
			data[i] = buf[i];

//		for (i=0; i<len; i++)
//			System.out.println("" + data[i]);
//		System.out.println("");
	}

	static void testDoubleSort()
	{
		double data[] = { .44, .69, .22, .92, .33, .71, .58, 
						  .22, .21, .88, .56, .39, .85, .10 };
		sortDoubleAsc(data, data.length);
		double data2[] = { .44, .69, .22, .92, .33, .71, .58, .49,
						  .22, .21, .88, .56, .39, .85, .10, .01 };
		sortDoubleAsc(data2, data2.length);
	}

	public static void main(String[] args)
	{
		Sorter.testDoubleSort();
	}
}
