
import java.lang.Runnable;

public class MonitorRelay implements Runnable
{
	public MonitorRelay(DisplayFrame win)
	{
		m_displayFrame = win;
	}

	public void run()
	{
		m_displayFrame.updateDisplay();
	}

	protected DisplayFrame		m_displayFrame;
}

