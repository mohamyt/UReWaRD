
import java.io.*;
import java.util.*;

// assumes input files are ASCII - run native2ascii on any files
// carrying extended characters
// 
// reads tab and CSV files - does not support quoted text in CSV (quotes are copied)
public class LCsvFileReader 
{
	public LCsvFileReader()
	{
		m_line = new ArrayList();
	}

	public void write(String file) throws IOException
	{
		int i;
		String str;
		BufferedWriter out = new BufferedWriter(new FileWriter(file));
		for (i=0; i<m_line.size(); i++)
		{
			str = (String) m_line.get(i);
			out.write(str, 0, str.length());
			out.newLine();
		}
		out.flush();
		out.close();
	}

	public void reset()
	{
		m_line.clear();
	}

	public void addLine(String str)
	{
		m_line.add(str);
	}

	public void load(File file) throws IOException
	{
		load(file.getAbsolutePath());
	}

	public void load(String file) throws IOException
	{
		reset();
		String s;
		String z;
		String token;
//		StringTokenizer t;
		int i;
		char c;

		BufferedReader in = new BufferedReader(new FileReader(file));
		ArrayList tokenList = new ArrayList();
		while ((s = in.readLine()) != null)
		{
			// skip lines that start with '#'
			if (s.startsWith("#") == true)
				continue;
			z = "";
			for (i=0; i<s.length(); i++)
			{
				c = s.charAt(i);
				if ((c == 9) || (c == 10) || (c == 13) || (c == ','))
				{
					// close token
					tokenList.add(z);
					z = "";
				}
				else if ((z.length() > 0) || (c != ' '))
					z += c; // strip white space from leading side of token
			}
			tokenList.add(z);

			// check token list to see if it has a valid string
			for (i=0; i<tokenList.size(); i++)
			{
				// break on non-empty string
				token = (String) tokenList.get(i);
				if (token.length() != 0)
				{
					break;
				}
			}

			if (i < tokenList.size())
			{
				m_line.add(tokenList);
				tokenList = new ArrayList();
			}
			else
			{
				tokenList.clear();
			}
		}
		in.close();
	}

	public String get(int row, int col)
	{
		String retVal = "";
		if (row < m_line.size())
		{
			ArrayList tokenList = (ArrayList) m_line.get(row);
			if (col < tokenList.size())
			{
				retVal = (String) tokenList.get(col);
			}
		}
		// if element not found, return null, "" or throw exception???
		// for now, return ""
		return retVal;
	}

	public int numRows()
	{
		return m_line.size();
	}

	public int numCols(int row)
	{
		if (row < m_line.size())
		{
			ArrayList tokenList = (ArrayList) m_line.get(row);
			return tokenList.size();
		}
		else 
			return 0;
	}

	private ArrayList	m_line;

/////////////////////////////////////////////////////////////
	
	public static void main(String[] args)
	{
		LCsvFileReader rdr = new LCsvFileReader();
		try 
		{
			PrintWriter out = new PrintWriter(
				new FileOutputStream(".tfr.tmp"));

			out.println("abc");
			out.println("def\tghi");
			out.println("jkl");
			out.println("");
			out.println("mno\tpqr");
			out.println("stu\tvwx\tyz-");
			out.println("ABC\tDEF");
			out.println("GHI");
			out.println("");
			out.println("JKL");
			out.println("MNO\tPQR");
			out.println("STU\tVWX\tYZ_");
			out.close();
			
			rdr.load(".tfr.tmp");
			int r = rdr.numRows();
			String s;
			int c;
			for (int i=0; i<r; i++)
			{
				c = rdr.numCols(i);
				for (int j=0; j<c; j++)
				{
					if (j > 0)
						System.out.print("\t");
					s = rdr.get(i, j);
					System.out.print(s);
				}
				System.out.println("");
			}
		}
		catch (IOException e)
		{
			System.out.println(e);
		}
	}
}
