/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package main.swing;

import gameboy.game.Cartridge;
import gameboy.game.CartridgeFactory;
import gameboy.io.Input;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.util.LinkedList;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import main.swing.utilities.Debugger;
import main.swing.utilities.RomViewer;

/**
 *
 * @author Colin Halseth
 */
public class SwingLauncher extends JFrame{
     
    public String romLocation = "./roms/";
    
    public boolean autoPlay = false;
    public boolean enableDebugger = true;
    public boolean enableTrace = false;
    public int[] launchSize = new int[]{166,144};
    public int frameRateLimit = -1;
    
    public int key_up = KeyEvent.VK_UP;
    public int key_down = KeyEvent.VK_DOWN;
    public int key_left = KeyEvent.VK_LEFT;
    public int key_right = KeyEvent.VK_RIGHT;
    public int key_select = KeyEvent.VK_SPACE;
    public int key_start = KeyEvent.VK_ENTER;
    public int key_a = KeyEvent.VK_X;
    public int key_b = KeyEvent.VK_Z;    
    
    public SwingLauncher(){}
    
    public void BuildFrame(){
        //Buid swing components
        this.setTitle("Gameboy Emulator");
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        JPanel contentPane = new JPanel();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));
        
        JPanel header = new JPanel();
        header.setPreferredSize(new Dimension(0,48));
        header.setBackground(new Color(25,96,211));
        contentPane.add(header);
        
        Cartridge[] carts = GetLocalCartridges();
        
        JList body = new JList(carts);
        body.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        body.setLayoutOrientation(JList.VERTICAL);
        body.setVisibleRowCount(-1);
        body.setFixedCellHeight(32);
        ((DefaultListCellRenderer)(body.getCellRenderer())).setHorizontalAlignment(SwingConstants.CENTER);
        ((DefaultListCellRenderer)(body.getCellRenderer())).setForeground(Color.WHITE);
        body.setBackground(Color.BLACK);
        body.setForeground(Color.WHITE);
        
        body.addMouseListener(new MouseAdapter(){
            @Override
            public void mouseReleased(MouseEvent e){
                int rowindex = body.locationToIndex(e.getPoint());
                if (rowindex < 0)
                    return;
                if(e.isPopupTrigger()){
                    JPopupMenu menu = new JPopupMenu();
                    JMenuItem ll = new JMenuItem("Launch");
                    ll.addActionListener((evt) -> {
                        Cartridge cart = carts[rowindex];
                        StartEmulator(cart); 
                    });
                    JMenuItem info = new JMenuItem("Cart Info");
                    info.addActionListener((evt) -> {
                        Cartridge cart = carts[rowindex];
                        RomViewer viewer = new RomViewer(cart);
                        viewer.setVisible(true);
                    });
                    menu.add(ll);
                    menu.add(info);
                    menu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
        
        JScrollPane scroller = new JScrollPane(body){
            public Dimension getPreferredSize(){
                return new Dimension(0, contentPane.getSize().height - 48);
            }
        };
        
        contentPane.add(scroller);
        
        JButton launch = new JButton("Launch");
        launch.addActionListener((evt) -> {
            if(body.getSelectedIndex() < 0)
                return;
            
            Cartridge cart = carts[body.getSelectedIndex()];
            StartEmulator(cart);             
        });
        
        JPanel footer = new JPanel();
        footer.setLayout(new GridLayout(-1,1));
        footer.add(launch);
        
        contentPane.add(footer);
        
        this.add(contentPane);
    }
    
    /**
     * Start the Swing GB emulator with the loaded configuration
     * @param cart 
     */
    public void StartEmulator(Cartridge cart){
        SwingGB gb = new SwingGB();
        gb.GetGameboy().LoadCartridge(cart);
        
        gb.GetGameboy().cpu.debugMode = enableTrace;
        gb.setFPS(this.frameRateLimit);
        gb.setRenderSize(this.launchSize[0], this.launchSize[1]);
        
        gb.GetGameboy().input.SetKey(Input.Key.Up, this.key_up);
        gb.GetGameboy().input.SetKey(Input.Key.Down, this.key_down);
        gb.GetGameboy().input.SetKey(Input.Key.Left, this.key_left);
        gb.GetGameboy().input.SetKey(Input.Key.Right, this.key_right);
        gb.GetGameboy().input.SetKey(Input.Key.Select, this.key_select);
        gb.GetGameboy().input.SetKey(Input.Key.Start, this.key_start);
        gb.GetGameboy().input.SetKey(Input.Key.A, this.key_a);
        gb.GetGameboy().input.SetKey(Input.Key.B, this.key_b);
        
        gb.setTitle("Playing: "+cart.header.title);
        gb.setVisible(true);
        
        if(enableDebugger){
            Debugger debugger = new Debugger(gb);
            debugger.setVisible(true);
        }
        
        if(autoPlay){
            gb.Play();
        }
    }
    
    /**
     * Get a list of all .gb files from the rom directory and create game Cartridges for them
     * @return 
     */
    public Cartridge[] GetLocalCartridges(){
        String[] locations = romLocation.split(",");

        LinkedList<Cartridge> carts = new LinkedList<Cartridge>();
        
        for(String loc : locations){
            loc = loc.trim();
            File dir = new File(loc);
            if(!dir.exists()){
                System.out.println("Invalid rom directory: "+loc);
                continue;
            }

            File[] files = dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".gb");
                }
            });
            
            for (File gbfile : files) {
                Cartridge rom = CartridgeFactory.Load(gbfile.getAbsolutePath());
                carts.add(rom);
            }
        }

        Cartridge[] roms = new Cartridge[carts.size()];
        roms = carts.toArray(roms);
        
        return roms;
    }
    
}
