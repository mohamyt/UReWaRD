import java.util.ArrayList;
import java.util.StringTokenizer;
import java.text.DecimalFormat;
import java.io.IOException;


class QStat 
{
	protected WaveMaker		m_wave;
	protected StatMaker		m_stat;
	
	public static int s_key = 4000;

	protected double m_waveSize, m_waveSD, m_iwi, m_iwiSD, m_vel, m_velSD;
	protected double m_h1, m_h2, m_h3, m_h4, m_d, m_p, m_k, m_gap;
	protected double m_runtime, m_dt;
	protected int m_warmup, m_rad;

	protected DecimalFormat	m_df;

	static final long serialVersionUID = 1;

	public QStat() 
	{
		m_h1 = 0;
		m_h2 = 0;
		m_h3 = 0;
		m_h4 = 0;
		m_d = 0;
		m_p = 0;
		m_k = 0;
		m_gap = 0;

		m_runtime = 0;
		m_warmup = 0;
		m_dt = 0;
		m_rad = 0;
		m_waveSize = 0;
		m_waveSD = 0;
		m_iwi = 0;
		m_iwiSD = 0;
		m_vel = 0;
		m_velSD = 0;

		m_df = new DecimalFormat();
		m_df.setMaximumFractionDigits(3);
	}

	public void runAnalysis(String fname) throws IOException
	{
		process("../" + fname);
		parseHeader(m_wave.header());
		s_key++;
		writeParamSql();

		readStats();
		writeStatSql();

		writeSimSql(fname);

		System.out.println("");
	}

	protected void readStats()
	{
		m_waveSize = m_stat.sizeMean();
		m_waveSD = m_stat.sizeSD();
		m_iwi = m_stat.iwiMean();
		m_iwiSD = m_stat.iwiSD();
		m_vel = m_stat.velMean();
		m_velSD = m_stat.velSD();
	}

	protected void writeParamSql()
	{
		System.out.println("INSERT INTO param_set ");
		System.out.println("  (id, h1, h2, h3, h4, ");
		System.out.println("  d, p, k, gap, ");
		System.out.println("  runtime, warmup, rad, dt) ");
		System.out.println("VALUES ");
		System.out.println("  ('"+s_key+"', '"+
					m_df.format(m_h1)+"', '"+
					m_df.format(m_h2)+"', '"+
					m_df.format(m_h3)+"', '"+
					m_df.format(m_h4)+"',");
		System.out.println("  '"+
					m_df.format(m_d)+"', '"+ 
					m_df.format(m_p)+"', '"+
					m_df.format(m_k)+"', '"+
					m_df.format(m_gap)+"', ");
		System.out.println("  '"+
					m_df.format(m_runtime)+"', '"+
					m_warmup+"', '"+ m_rad+"', '"+
					m_df.format(m_dt)+"');");
		System.out.println("");
	}

	protected void writeStatSql()
	{
		System.out.println("INSERT INTO stats ");
		System.out.println("  (param_id, wave_size, wave_sd, ");
		System.out.println("  iwi, iwi_sd, velocity, ");
		System.out.println("  velocity_sd) ");
		System.out.println("VALUES ");
		System.out.println("  ('"+s_key+"', '"+
					m_df.format(m_waveSize)+"', '"+
					m_df.format(m_waveSD)+"',");
		System.out.println("  '"+
					m_df.format(m_iwi)+"', '"+
					m_df.format(m_iwiSD)+"', '"+
					m_df.format(m_vel)+"',");
		System.out.println("  '"+m_df.format(m_velSD)+"');");
		System.out.println("");
	}

	protected void writeSimSql(String fname)
	{
		System.out.println("INSERT INTO simulation ");
		System.out.println("  (param_id, filename)");
		System.out.println("VALUES ");
		System.out.println("  ('"+s_key+"', '"+fname+"');");
		System.out.println("");
	}

	protected void parseHeader(String hdr)
	{
		StringTokenizer tok = new StringTokenizer(hdr);
		String val = "";
		String par = "";
		while (tok.hasMoreTokens() == true)
		{
			par = "";
			val = "";
			par = tok.nextToken();
			if (tok.hasMoreTokens() == false)
				break;
			val = tok.nextToken();
			if (par.equals("P_H1"))
				m_h1 = Double.parseDouble(val);
			else if (par.equals("P_H2"))
				m_h2 = Double.parseDouble(val);
			else if (par.equals("P_H3"))
				m_h3 = Double.parseDouble(val);
			else if (par.equals("P_H4"))
				m_h4 = Double.parseDouble(val);
			else if (par.equals("P_D"))
				m_d = Double.parseDouble(val);
			else if (par.equals("P_P"))
				m_p = Double.parseDouble(val);
			else if (par.equals("P_K"))
				m_k = Double.parseDouble(val);
			else if (par.equals("P_GAP"))
				m_gap = Double.parseDouble(val);
			else if (par.equals("dT"))
				m_dt = Double.parseDouble(val);
			else if (par.equals("rad"))
				m_rad = Integer.parseInt(val);
			else if (par.equals("runtime"))
				m_runtime = Double.parseDouble(val);
			else if (par.equals("warmup"))
				m_warmup = Integer.parseInt(val);
			else
			{
				System.err.println("Parse error in on token '"+par+"="+val+"'");
				System.err.println("Header: '"+hdr+"'");
				System.exit(1);
			}
		}
	}

	protected void process(String str) throws IOException
	{
		m_wave = new WaveMaker(str);
		m_stat = new StatMaker(m_wave);

		m_stat.analyze(false);
	}

	///////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////
	public static void main(String[] args)
	{
		try
//		{
//			QStat qs = new QStat();
//			qs.runAnalysis("data/amacrine_83.dat");
//			qs.runAnalysis("data/amacrine_84.dat");
//		}
		{
			QStat qs = new QStat();
			//for (int i=401; i<=541; i++)
			for (int i=100; i<=164; i++)
				qs.runAnalysis("amacrine_"+i+".dat");
		}
		catch (IOException ioe)
		{
				ioe.toString();
				ioe.printStackTrace();
		}
	}
}

