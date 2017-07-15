/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gameboy.gpu;

import gameboy.IMemory;
import gameboy.Listener;
import gameboy.MemoryMap;
import java.awt.Color;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

/**
 *
 * @author Colin Halseth
 */
public class Gpu implements IMemory{
    
    public static final int VRAM_SIZE = 8192;
    public static final int OAM_COUNT = 40;
    public static final int LCD_HEIGHT = 144;
    public static final int LCD_WIDTH = 160;
    
    public static final int GPU_SCANLINEOAM = 2;
    public static final int GPU_SCANLINEVRAM = 3;
    public static final int GPU_HBLANK = 0;
    public static final int GPU_VBLANK = 1;
    
    public static final int TIME_SCANLINEOAM = 80;
    public static final int TIME_SCANLINEVRAM = 172;
    public static final int TIME_HBLANK = 204;
    public static final int TIME_FULLLINE = TIME_SCANLINEOAM + TIME_SCANLINEVRAM + TIME_HBLANK;
    public static final int TIME_VBLANK = 4560;
    public static final int TIME_FULLFRAME = TIME_FULLLINE*LCD_HEIGHT + TIME_VBLANK;
    
    //References
    private MemoryMap mmu;
    
    //Drawing board
    public final Bitmap canvas = new Bitmap(LCD_WIDTH, LCD_HEIGHT);
    
    //VRAM
    private final int[] vram = new int[VRAM_SIZE];
    
    //Tilemap
    public final int[][][] tilemap = new int[512][8][8];
    
    //Colour pallet
    private final ColourPallet colours = new ColourPallet();
    private final Color[] bgpallet = new Color[4];
    private final Color[] obj0pallet = new Color[4];
    private final Color[] obj1pallet = new Color[4];
    
    //OAM data
    private final int[] oam = new int[OAM_COUNT * 4];
    public final Sprite[] oam_data = new Sprite[OAM_COUNT];
    private final Sprite[] oam_data_sorted = new Sprite[OAM_COUNT];
    
    //Internal values
    private final HashMap<Integer, Integer> reg = new HashMap<Integer, Integer>();
    private int yscroll = 0;
    private int xscroll = 0;
    private int ywindow = 0;
    private int xwindow = 0;
    private int curline = 0;
    private int curlineCheck = 0;
    private int gpumode = 2;
    private int clock = 0;
    
    public boolean windowon = true;
    public boolean lcdon = true;
    public boolean largeobj = false;
    public boolean objon = true;
    public boolean bgon = true;
    public boolean coincidenceInterruptEnable = false;
    public boolean oamInterruptEnable = false;
    public boolean vblankInterruptEnable = false;
    public boolean hblankInterruptEnable = false;
    
    public boolean windowtile = false;
    public boolean tiledatatable = false; //false = 0x0000;  true = 0x0800
    public boolean tilemapdisplayselect = false;  //false = 0x1800;  true = 0x1C00
    
    public Listener OnVBlank;
    
    public Gpu(){ 
        //OAM data
        Arrays.fill(oam, 0);
        for(int i = 0; i < oam_data.length; i++){
            oam_data[i] = new Sprite(i);
            oam_data_sorted[i] = oam_data[i];
        }
        
        Reset();
    }
   
    public void Step(int step){
        if(!lcdon)
            return;
        
        clock += step * 4;  //Step is in m time not in cycles (t)
        
        switch(gpumode){
            case GPU_SCANLINEOAM: //Scanline OAM
                if(clock >= TIME_SCANLINEOAM){
                    //Enter VRAM read mode
                    gpumode = Gpu.GPU_SCANLINEVRAM;
                    clock = 0;
                }
                break;
            case GPU_SCANLINEVRAM: //Scanline VRAM
                if(clock >= TIME_SCANLINEVRAM){
                    gpumode = Gpu.GPU_HBLANK;
                    clock = 0;
                    
                    renderScanline();
                }
                break;
            case GPU_HBLANK: //Horizontal Blank
                if(clock >= TIME_HBLANK){
                    clock = 0;
                    curline++;
                    
                    //Check if we need to flush the buffer (ie screen drawing done)
                    if(curline == 143){
                        gpumode = Gpu.GPU_VBLANK;
                        flushBuffer();
                        mmu.i_flags |= 1; //Raise interupt
                    }else{
                        gpumode = Gpu.GPU_SCANLINEOAM;
                    }
                }
                break;
            case GPU_VBLANK: //Vertical Blank
                if(clock >= TIME_VBLANK){
                    clock = 0;
                    curline = 0;
                    gpumode = Gpu.GPU_SCANLINEOAM;
                }
                break;
        }
    }
    
    public void renderScanline(){
        if(bgon){
            renderTiles();
        }
        
        if(objon){
            renderSprites();
        }
    }
    
    public void renderTiles(){
        
    }
    
    public void renderSprites(){
        //TODO
    }
    
    public void flushBuffer(){
        if(this.OnVBlank != null)
            this.OnVBlank.OnEvent();
    }
    
    @Override
    public void Reset() {
        //Drawing board
        canvas.Fill(Color.WHITE);
        
        //Tilemap
        for (int i = 0; i < 512; i++) {
            for (int j = 0; j < 8; j++) {
                for (int k = 0; k < 8; k++) {
                    tilemap[i][j][k] = 0;
                }
            }
        }
        
        //VRAM
        Arrays.fill(vram, 0);
        
        //Reset OAM
        Arrays.fill(oam, 0);
        for(int i = 0; i < oam_data.length; i++){
            oam_data[i].Reset();
        }
        
        //Colour Pallet
        Arrays.fill(bgpallet, colours.bg.WHITE);
        Arrays.fill(obj0pallet, colours.obj0.WHITE);
        Arrays.fill(obj1pallet, colours.obj1.WHITE);
        
        //Inernal values
        yscroll = 0;
        xscroll = 0;
        curline = 0;
        curlineCheck = 0;
        gpumode = 2;
        ywindow = 0;
        xwindow = 0;
        
        windowon = true;
        lcdon = true;
        largeobj = false;
        objon = true;
        bgon = true;
    
        windowtile = false;
        tiledatatable = false;
        tilemapdisplayselect = false;
        
        reg.clear();
    }
    
    protected void updatetile(int addr, int value){
        int saddr = addr;
        if((addr & 1) != 0){
            saddr --;
            addr --;
        }
        
        int tile = (addr >> 4) & 511;
        int y = (addr >> 1) & 7;
        
        int sx;
        for(int x = 0; x < 8; x++){
            sx = 1 << (7 - x);
            tilemap[tile][y][x] = ((vram[saddr] & sx) != 0 ? 1 : 0) | ((vram[saddr + 1] & sx) != 0 ? 2 : 0);
        }
        
    }
    
    protected void updateoam(int addr, int value) {
        addr -= 0xFE00;
        int obj = addr >> 2; //Divide by 4
        if (obj > 40 || obj < 0) {
            return;
        }

        switch (addr & 3) {
            case 0:
                oam_data[obj].y = value - 16;
                break;
            case 1:
                oam_data[obj].x = value - 8;
                break;
            case 2:
                if (largeobj) {
                    oam_data[obj].tile = (value & 0xFE);
                } else {
                    oam_data[obj].tile = value;
                }
                break;
            case 3:
                oam_data[obj].priority = ((value & 0x80) != 0 ? Sprite.Priority.BelowBackground : Sprite.Priority.AboveBackground );
                oam_data[obj].xflip = ((value & 0x20) != 0 ? Sprite.XOrientation.Flipped : Sprite.XOrientation.Normal);
                oam_data[obj].yflip = ((value & 0x40) != 0 ? Sprite.YOrientation.Flipped : Sprite.YOrientation.Normal);
                oam_data[obj].objPalette = ((value & 0x10) != 0 ? Sprite.Palette.One : Sprite.Palette.One);
                break;
        }

	//Resort sorted oam_data;
        Arrays.sort(oam_data_sorted, new Comparator<Sprite>(){
            @Override
            public int compare(Sprite t, Sprite t1) {
                if(t.x > t1.x)
                    return -1;
                if(t.id > t1.id)
                    return -1;
                return 0;
            }
        });
    }

    @Override
    public int rb(int addr) {
        //VRAM
        if(addr >= 0x8000 && addr <= 0x9FFF){
            return vram[addr&0x1FFF];
        }

        //OAM
        if(addr >= 0xFE00 && addr <= 0xFE9F){
            return oam[addr & 0xFF];
        }
        
        //Registers
        switch(addr){
            case 0xFF40:    //LCD Control
                return (lcdon ? 0x80 : 0) | 
                        (largeobj ? 0x04 : 0) | 
                        (objon ? 0x02 : 0) | 
                        (bgon ? 0x01 : 0) | 
                        ((tiledatatable) ? 0x10 : 0) | 
                        ((tilemapdisplayselect) ? 0x08 : 0) |
                        (windowon ? 0x20 : 0) |
                        (windowtile? (0x40) : 0);
            case 0xFF41:    //LCD Status
                    int v = 0;
                    if(coincidenceInterruptEnable) v |= 0x40;
                    if(oamInterruptEnable) v |= 0x20;
                    if(vblankInterruptEnable) v |= 0x10;
                    if(hblankInterruptEnable) v |= 0x08;
                    if(curline == curlineCheck) v |= 0x04;
                    v |= gpumode;
                    return v;
            case 0xFF42:    //Scroll Y         
                return yscroll;
            case 0xFF43:    //Scroll X         
                return xscroll;
            case 0xFF44:    //Current scanline
                return curline;
            case 0xFF45:    //Raster?
                return curlineCheck;
            case 0xFF47:    //Background Pallet
            case 0xFF48:    //Object Pallet 0
            case 0xFF49:    //Object Pallet 1
            default:
                if(reg.containsKey(addr))
                    return reg.get(addr);
                return 0;
            case 0xFF4A:    //Window Y
                return ywindow;
            case 0xFF4B:    //Window X
                return xwindow;
        }
    }

    @Override
    public void wb(int addr, int value) {
        //VRAM
        if(addr >= 0x8000 && addr <= 0x9FFF){
            System.out.println("Write to VRAM "+addr+" at index "+ (addr&0x1FFF)+" the value "+value);
            vram[addr&0x1FFF] = value;
            updatetile(addr&0x1FFF, value);
            
            return;
        }
        
        //OAM
        if(addr >= 0xFE00 && addr <= 0xFE9F){
            System.out.println("Write to OAM "+addr+" at index "+ (addr & 0xFF)+" the value "+value);
            oam[addr & 0xFF] = value;
            updateoam(addr, value);
            return;
        }
        
        reg.put(addr, value);
        switch(addr){
            case 0xFF40:    //LCD Control
                lcdon = (value & 0x80) != 0;                    //BIT 7 - LCD Display (1=on, 0=off)
                windowtile = (value & 0x40) != 0;               //BIT 6 - Window Tile Map Display (0=9800-9BFF, 1=9C00-9FFF)
                windowon = (value & 0x20) != 0;                 //BIT 5 - Window Tile Display Enable (0=off, 1=on)
                tiledatatable = ((value&0x10) != 0);            //BIT 4 - BG Tile Data Select (0=8800-97FF, 1=8000-8FFF)
                tilemapdisplayselect = ((value&0x08) != 0);     //BIT 3 - BG Tile Map Select (0=9800-9BFF, 1=9C00-9FFF)
                largeobj = (value&0x04) != 0;                   //BIT 2 - Sprite Size (0=8x8, 1=8x16)
                objon = (value&0x02) != 0;                      //BIT 1 - Sprite Display (0=off, 1=on)
                bgon = (value&0x01) != 0;                       //BIT 0 - BG Display (0=off, 1=on)
                break;
            case 0xFF41:    //LCD Status
                coincidenceInterruptEnable = (value & 0x40) == 0x40;
                oamInterruptEnable = (value & 0x20) == 0x20;
                vblankInterruptEnable = (value & 0x10) == 0x10;
                hblankInterruptEnable = (value & 0x08) == 0x08;
                gpumode = (value & 0x03);
                break;
            case 0xFF42:    //Scroll Y         
                yscroll = value;
                break;
            case 0xFF43:    //Scroll X         
                xscroll = value;
                break;
            case 0xFF44:    //Current scanline
                curline = value;
                break;
            case 0xFF45:    //Raster?
                curlineCheck = value;
                break;
            case 0xFF46:    //Object Attribute Memory OAM Direct Data Transfer
                for(int i = 0; i < 160; i++){
                    int v = mmu.rb((value << 8) + i);
                    oam[i] = v;
                    updateoam(0xFE00 + i, v);
                }
                break;
            case 0xFF47:    //Background Pallet
                for(int i = 0; i < 4; i++){
                    int v = (value >> (i * 2)) & 3; //1,2,3
                    switch(v){
                        case 0: bgpallet[i] = colours.bg.WHITE; break;
                        case 1: bgpallet[i] = colours.bg.LIGHT; break;
                        case 2: bgpallet[i] = colours.bg.MEDIUM; break;
                        case 3: bgpallet[i] = colours.bg.DARK; break;
                    }
                }
                break;
            case 0xFF48:    //Object Pallet 0
                for(int i = 0; i < 4; i++){
                    int v = (value >> (i * 2)) & 3; //1,2,3
                    switch(v){
                        case 0: obj0pallet[i] = colours.obj0.WHITE; break;
                        case 1: obj0pallet[i] = colours.obj0.LIGHT; break;
                        case 2: obj0pallet[i] = colours.obj0.MEDIUM; break;
                        case 3: obj0pallet[i] = colours.obj0.DARK; break;
                    }
                }
                break;
            case 0xFF49:    //Object Pallet 1
                for(int i = 0; i < 4; i++){
                    int v = (value >> (i * 2)) & 3; //1,2,3
                    switch(v){
                        case 0: obj1pallet[i] = colours.obj1.WHITE; break;
                        case 1: obj1pallet[i] = colours.obj1.LIGHT; break;
                        case 2: obj1pallet[i] = colours.obj1.MEDIUM; break;
                        case 3: obj1pallet[i] = colours.obj1.DARK; break;
                    }
                }
                break;
            case 0xFF4A:    //Window Y
                ywindow = value;
                break;
            case 0xFF4B:    //Window X
                xwindow = value;
                break;
        }
    }
    
    @Override
    public void SetMMU(MemoryMap mmu){
        this.mmu = mmu;
    }
    
}
