
import java.awt.event.*;
import javax.swing.*;
import java.awt.*;

public class DisplayFrame extends JFrame
{
	protected int	m_width = 0;
	protected int	m_height = 0;
	public static int	s_count = 0;
	public static boolean	s_exitOnClose = true;

	protected Box m_controls = null;

	protected ImagePane	m_drawingPane = null;

	public void hookMousePressed(MouseEvent e)	{ ; }
	public void hookMouseReleased(MouseEvent e)	{ ; }
	public void hookMouseClicked(MouseEvent e)	{ ; }

	static final long serialVersionUID = 1001;

	public DisplayFrame(String name, int x, int y, int w, int h)
	{
		super(name);
		Container cont = getContentPane();
		BorderLayout layout = new BorderLayout();
		cont.setLayout(layout);
		m_width = w;
		m_height = h;
		padWindowList();
		m_drawingPane = new ImagePane(this);
		m_drawingPane.setSize(w, h);
		cont.add(m_drawingPane, "Center");

		m_controls = Box.createHorizontalBox();
		cont.add(m_controls, "North");
		
		//setBounds(x, y, w, h);
		setBounds(x, y, w+12, h+27);
		setVisible(true);

		// size shouldn't exceed 2, but give some extra space just in case
		m_nextImage = null;

		m_drawingPane.m_img = exchangeImage(null, 1);
		m_drawingPane.m_gc = m_drawingPane.m_img.getGraphics();

		addWindowListener(new WindowAdapter()
		{
			public void windowClosing(WindowEvent e)
			{
				handleClose();
			}
		});
		
		KeyListener kl = new KeyListener()
		{
			public void keyPressed(KeyEvent e)
			{
				handleKeyPress(e);
			}
			public void keyTyped(KeyEvent e)
			{
				handleKeyTyped(e);
			}
			public void keyReleased(KeyEvent e)
			{
			}
		};
		addKeyListener(kl);
		cont.addKeyListener(kl);
	}

	public void addControl(Component control)
	{
		m_controls.add(Box.createHorizontalStrut(3));
		m_controls.add(control);
		validate();
	}

	public void handleKeyTyped(KeyEvent e)
	{
		// do nothing
	}
	
	public void handleKeyPress(KeyEvent e)
	{
		char c = e.getKeyChar();
		if (c == KeyEvent.VK_ESCAPE)
		{
			handleClose();
			e.consume();
		}
		else if (c == KeyEvent.VK_Q)
		{
			System.exit(0);
		}
	}

	public synchronized static void unpadWindowList()
	{
		DisplayFrame.s_count--;
		if (DisplayFrame.s_count <= 0)
			System.exit(0);
	}
	
	public synchronized static void padWindowList()
	{
		DisplayFrame.s_count++;
	}
	
	protected synchronized void handleClose()
	{
		if ((--DisplayFrame.s_count <= 0) && (s_exitOnClose))
			System.exit(0);
		else
		{
			//System.out.println("Last window closed - exiting");
			dispose();
		}
	}

	// modes
	//	1	producer requesting blank image
	//	2	new image submitted from producer
	//	3	request received to update display image
	public synchronized Image exchangeImage(Image img, int mode)
	{
		if (mode == 1)
		{
			return m_drawingPane.createImage(m_width, m_height);
		}
		else if (mode == 2)
		{
			m_nextImage = img;
		}
		else if (mode == 3)
		{
			// update (repaint) request received
			// only window thread should exchange with this mode
			if (m_nextImage != null)
			{
				m_drawingPane.m_img = m_nextImage;
				m_drawingPane.m_gc = m_drawingPane.m_img.getGraphics();
			}
else System.out.println("next image null");
		}

		return null;
	}

	public Image getNewImage()
	{
		return m_drawingPane.createImage(m_width, m_height);
	}

	public Image getImage()
	{
		if (m_nextImage == null)
			m_nextImage = getNewImage();

		return m_nextImage;
	}

	public void setImage(Image img)
	{
		m_nextImage = img;
	}

	// NOTE: this function should ONLY be called through the MonitorRelay
	//	message sender or race conditions and possible lockup will result
	public void updateDisplay()
	{
		if (m_nextImage != null)
		{
			m_drawingPane.m_img = m_nextImage;
			m_drawingPane.m_gc = m_drawingPane.m_img.getGraphics();
		}
else System.out.println("next image null");
		m_drawingPane.repaint();
	}
	
	////////////////////////////////////////////////////////
	// called by Animant when image data ready to be updated
	public void requestDataUpdate()
	{
		MonitorRelay mr = new MonitorRelay(this);
		SwingUtilities.invokeLater(mr);
	}

	///////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////
	
	//public static final int		x_offset	= 6;
	//public static final int		y_offset	= 20;

	protected Image		m_nextImage;

	class ImagePane extends JComponent //Container 
	{
		public Image	m_img = null;
		public Graphics	m_gc = null;
		DisplayFrame m_parent = null;

		static final long serialVersionUID = 1002;

		public ImagePane(DisplayFrame parent)
		{
			super();
			m_parent = parent;

			addKeyListener(new KeyListener()
			{
				public void keyPressed(KeyEvent e)
				{
					m_parent.handleKeyPress(e);
				}
				public void keyTyped(KeyEvent e)
				{
					m_parent.handleKeyTyped(e);
				}
				public void keyReleased(KeyEvent e)
				{
				}
			});

			addMouseListener(new MouseListener()
			{
				public void mousePressed(MouseEvent e)
				{
					hookMousePressed(e);
				}

				public void mouseReleased(MouseEvent e)
				{
					hookMouseReleased(e);
				}

				public void mouseClicked(MouseEvent e) 
				{ 
					hookMouseClicked(e); 
				}
				public void mouseEntered(MouseEvent e) { ; }
				public void mouseExited(MouseEvent e) { ; }
			});
		}

		public void paint(Graphics g)
		{
			if (m_img != null)
				g.drawImage(m_img, 0, 0, this);
		}

		public void update(Graphics g)
		{
			paint(g);
		}
	}
}

