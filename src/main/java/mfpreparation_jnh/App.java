package mfpreparation_jnh;

import java.awt.event.WindowEvent;
import java.util.Date;

import javax.swing.UIManager;
import javax.swing.filechooser.FileSystemView;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.io.FileInfo;
import ij.io.OpenDialog;
import ij.io.Opener;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.plugin.PlugIn;
import ij.process.StackProcessor;
import ij.text.TextPanel;
import mfpreparation_jnh.jnhsupport.*;

/***===============================================================================

MultiFocal_Preparation

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation (http://www.gnu.org/licenses/gpl.txt )

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

See the GNU General Public License for more details.

Copyright (C) 2017 Jan N Hansen 
  
For any questions please feel free to contact me (jan.hansen@uni-bonn.de).

==============================================================================**/
public class App implements PlugIn, Measurements{
	//Name
	static final String PluginName = "MultiFocal_Preparation";
	static final String PluginVersion = "0.0.1";

	//processing
	static final String[] taskVariant = {"active image in FIJI","multiple images (open multi-task-manager)", "all images open in FIJI"};
	String selectedTaskVariant = taskVariant[0];
	int tasks = 1;
		
	boolean selectRegion = true, intensityCorrection = true, split = true, register = true, saveDate = true, saveSeparately = false;
	
	static final String[] substractVariant = {"nothing","minimum projection", "maximum projection", "average projection"};
	String selectedSubstractVariant = substractVariant[0];
	
	//dialog	
	boolean done = false;		
	ProgressDialog progress;
	
@Override
public void run(String arg) {	
	/**&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
	GenericDialog
	&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&*/
	
		GenericDialog gd = new GenericDialog(PluginName);		
//		setInsets(top, left, bottom)
		gd.setInsets(0, 0, 0);	gd.addMessage(PluginName + ", version " + PluginVersion + " (\u00a9 2016-" + constants.dateY.format(new Date()) + ", JN Hansen)", constants.Head1);
		
		gd.setInsets(10,0,0);	gd.addChoice("Process ", taskVariant, selectedTaskVariant);
		
		gd.setInsets(10, 0, 0);	gd.addCheckbox("Crop to ROI...", selectRegion);
		gd.setInsets(0, 30, 0);	gd.addMessage("You will be asked to open a Roi for all images later.", constants.BoldTxt);
		
		gd.setInsets(10, 0, 0);	gd.addCheckbox("Correct intensities...", intensityCorrection);
		gd.setInsets(0, 30, 0);	gd.addMessage("You will be asked to open an intensity map for all images later.", constants.BoldTxt);
		
		gd.setInsets(10,0,0);	gd.addChoice("Substract ", substractVariant, selectedSubstractVariant);
		
		gd.setInsets(10, 0, 0);	gd.addCheckbox("Split image into four planes...", split);
		gd.setInsets(0, 30, 0);	gd.addMessage("Plane alignment in stack:", constants.BoldTxt);
		gd.setInsets(0, 30, 0);	gd.addMessage("4 | 1", constants.BoldTxt);
		gd.setInsets(0, 30, 0);	gd.addMessage("2 | 3", constants.BoldTxt);
		
		gd.setInsets(10, 0, 0);	gd.addCheckbox("Register planes using registration matrix file", register);
		gd.setInsets(0, 30, 0);	gd.addMessage("For processing the FIJI-plugin MultiStackReg_ is required (plugin by Brad Busse, http://bradbusse.net/downloads.html).", constants.PlTxt);
		gd.setInsets(-5, 30, 0);	gd.addMessage("In the next step, you will be asked to open a MultiStackReg-registration file to register the 4 plane images.", constants.PlTxt);
		
		gd.setInsets(10, 0, 0);	gd.addCheckbox("save individual planes separately", saveSeparately);
		
		gd.setInsets(10, 0, 0);	gd.addCheckbox("save date in output filename", saveDate);
		gd.showDialog();
	
		selectedTaskVariant = gd.getNextChoice();
		selectRegion = gd.getNextBoolean();
		intensityCorrection = gd.getNextBoolean();
		selectedSubstractVariant = gd.getNextChoice();
		split = gd.getNextBoolean();
		register = gd.getNextBoolean();
		saveSeparately = gd.getNextBoolean();
		saveDate = gd.getNextBoolean();
		
		if (gd.wasCanceled())return;
		
	/**&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
	Initiate multi task management
	&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&*/
	try{
		UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
	}catch(Exception e){}
	
	//image selection
	String name [] = {"",""};
	String dir [] = {"",""};
	ImagePlus allImps [] = new ImagePlus [2];		
		
	if(selectedTaskVariant.equals(taskVariant[1])){
		OpenFilesDialog od = new OpenFilesDialog ();
		od.setLocation(0,0);
		od.setVisible(true);
		
		od.addWindowListener(new java.awt.event.WindowAdapter() {
	        public void windowClosing(WindowEvent winEvt) {
//		        	IJ.log("Analysis canceled!");
	        	return;
	        }
	    });
	
		//Waiting for od to be done
		{
			//wating for response of od
			while(od.done==false){
				 try{
					 Thread.currentThread().sleep(50);
			     }catch(Exception e){
			     }
			}
		}
		
		tasks = od.filesToOpen.size();
		name = new String [tasks];
		dir = new String [tasks];
		for(int task = 0; task < tasks; task++){
			name[task] = od.filesToOpen.get(task).getName();
			dir[task] = od.filesToOpen.get(task).getParent() + System.getProperty("file.separator");
		}		
	}else if(selectedTaskVariant.equals(taskVariant[0])){
		if(WindowManager.getIDList()==null){
			new WaitForUserDialog("Plugin canceled - no image open in FIJI!").show();
			return;
		}
		FileInfo info = WindowManager.getCurrentImage().getOriginalFileInfo();
		name = new String [1];
		dir = new String [1];
		name [0] = info.fileName;	//get name
		dir [0] = info.directory;	//get directory
		tasks = 1;
	}else if(selectedTaskVariant.equals(taskVariant[2])){	// all open images
		if(WindowManager.getIDList()==null){
			new WaitForUserDialog("Plugin canceled - no image open in FIJI!").show();
			return;
		}
		int IDlist [] = WindowManager.getIDList();
		tasks = IDlist.length;	
		if(tasks == 1){
			selectedTaskVariant=taskVariant[0];
			FileInfo info = WindowManager.getCurrentImage().getOriginalFileInfo();
			name = new String [1];
			dir = new String [1];
			name [0] = info.fileName;	//get name
			dir [0] = info.directory;	//get directory
		}else{
			name = new String [tasks];
			dir = new String [tasks];
			allImps = new ImagePlus [tasks];
			for(int i = 0; i < tasks; i++){
				allImps[i] = WindowManager.getImage(IDlist[i]); 
				FileInfo info = allImps[i].getOriginalFileInfo();
				name [i] = info.fileName;	//get name
				dir [i] = info.directory;	//get directory
			}		
		}				
	}
	
	boolean tasksSuccessfull [] = new boolean [tasks];
	for(int task = 0; task < tasks; task++){
		tasksSuccessfull [task] = false;
	}	
	
	//start progress dialog
	progress = new ProgressDialog(name, tasks);
	progress.setVisible(true);
	progress.addWindowListener(new java.awt.event.WindowAdapter() {
        public void windowClosing(WindowEvent winEvt) {
        	progress.stopProcessing();
        	if(done==false){
        		IJ.error("Script stopped...");
        	}       	
        	System.gc();
        	return;
        }
	});	
	
	/**&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
	Processing
	&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&*/
	
	//Initialize
	ImagePlus imp;		
	String homePath = FileSystemView.getFileSystemView().getHomeDirectory().getAbsolutePath();
	
	// open intensity correction map
	double [][] corFactors = null;
	String corrName = "";
	String corrDir;
	if(intensityCorrection){
		ImagePlus corImp;
		{
			OpenDialog od = new OpenDialog("open reference image", null);
			corrName = od.getFileName();
		  	corrDir = od.getDirectory();
		}
		corImp = IJ.openImage(corrDir+corrName);
		if(corImp.getStackSize()>1){
			IJ.error("Intensity map is not in right format!");
		}
		
		corFactors = new double [corImp.getWidth()][corImp.getHeight()];
		double maxIntensity = 0.0;
		for(int x = 0; x < corImp.getWidth(); x++){
			for(int y = 0; y < corImp.getHeight(); y++){
				corFactors [x][y] = corImp.getStack().getVoxel(x, y, 0);
				if(corFactors[x][y] > maxIntensity){
					maxIntensity = corFactors [x][y];
				}
			}
		}
		corImp.changes = false;
		corImp.close();
		
		for(int x = 0; x < corImp.getWidth(); x++){
			for(int y = 0; y < corImp.getHeight(); y++){
				if(corFactors[x][y] != 0.0){
					corFactors [x][y] = maxIntensity/(corFactors [x][y]);
				}				
			}
		}
	}
	
	//Select Region
	Roi selection = null;
	String regionName = "";
	String regionDir = "";
	if(selectRegion){
		OpenDialog od = new OpenDialog("Select ROI for region selection", null);
		regionName = od.getFileName();
		regionDir = od.getDirectory();		
		Opener o = new Opener();
		selection = o.openRoi(regionDir + regionName);		
	}
	
	
	
	// open registration file
	OpenDialog om = null;
	if(register){
		om = new OpenDialog("open matrix file", null);
	}
	
	//initialize
	Date startDate;
	TextPanel tp;
	//processing
	tasking: for(int task = 0; task < tasks; task++){
		progress.updateBarText("in progress...");
		startDate = new Date();
		progress.clearLog();
//		try{
			running: while(true){
				// get image and open
					try{
				   		if(selectedTaskVariant.equals(taskVariant[1])){
				   			imp = IJ.openImage(""+dir[task]+name[task]+"");	
							imp.deleteRoi();
				   		}else if(selectedTaskVariant.equals(taskVariant[0])){
				   			imp = WindowManager.getCurrentImage().duplicate();
				   			imp.deleteRoi();
				   			imp.lock();				   			
				   		}else{
				   			imp = allImps[task].duplicate();
				   			imp.lock();
				   			imp.deleteRoi();
				   		}
				   	}catch (Exception e) {		
						progress.notifyMessage("Task " + (task+1) + "/" + tasks + ": file is no image - could not be processed!",ProgressDialog.ERROR);
						progress.moveTask(task);	
						break running;
					}
					
			    	String saveName = name [task].substring(0,name [task].lastIndexOf(".")) + "_";
			    	if(selectRegion)		saveName += "Cr";
			    	if(intensityCorrection) saveName += "Ic";
			    	if(!selectedSubstractVariant.equals(substractVariant[0])) saveName += "-P";
			    	if(split) 				saveName += "Sp";
			    	if(register) 			saveName += "R";		
			    	if(saveDate)			saveName += "_" + constants.dateName.format(startDate);
			    	String savePath = dir [task]+ saveName;
		    	// get image and open (end)	
				
			    // select Region
			    	if(selectRegion){
			    		Calibration cal = imp.getCalibration();
			    		imp.setRoi(selection);
			    		StackProcessor proc = new StackProcessor(imp.getStack());
			    		ImageStack stack = proc.crop(selection.getPolygon().getBounds().x, selection.getPolygon().getBounds().y, 
			    				selection.getPolygon().getBounds().width, selection.getPolygon().getBounds().height);
			    		imp = new ImagePlus(imp.getTitle(),stack);
			    		imp.setCalibration(cal);
			    		System.gc();
				    	
//				    	userCheck(imp);
			    	}
			    // select Region
			    	
			    // intensity correction			    	
			    	if(intensityCorrection && corFactors != null){
			    		imp = createICorrImage(imp, corFactors);
//			    		userCheck(imp);
			    	}
			    // intensity correction
			    	
			    // image substraction
			    	if(!selectedSubstractVariant.equals(substractVariant[0])){
			    		ImagePlus substractImage = null;
			    		if(selectedSubstractVariant.equals(substractVariant[1])){	//minimum
			    			substractImage = minIntensityProjection(imp);
			    		}else if(selectedSubstractVariant.equals(substractVariant[2])){	//maximum
			    			substractImage = impProcessing.maxIntensityProjection(imp);
			    		}else if(selectedSubstractVariant.equals(substractVariant[3])){	//average
			    			substractImage = impProcessing.averageIntensityProjection(imp);
			    		}
			    		imp = substractedImage(imp, substractImage);
			    		System.gc();
//			    		userCheck(imp);
			    	}
		    	// image substraction
			    	
				//divide image into 4
			    	if(split){
			    		progress.updateBarText("splitting image...");
					  	imp = impProcessing.splitToStack(imp);
					  	if(imp == null){	//stop program if imp could not be split
					  		progress.notifyMessage("splitting images failed - image is already a stack image!", ProgressDialog.ERROR);
					  		break running;
					  	}
					  	if(progress.isStopped())break running;
			    	}					
				//divide image into 4
			  	
				//register
				  	if(register){
				  		progress.updateBarText("register image...");
					  	imp = registerByFile(imp,om.getPath(),progress);
					  	if(progress.isStopped())break running;
				  	}
				//register  	
			  	
				//save separately
				  	if(saveSeparately){
				  		saveSlicesIndividually(imp, savePath);
				  	}
				//save separately
				  	
			  	//save registered image
				  	if(!saveSeparately){
				  		IJ.saveAsTiff(imp, savePath + ".tif");
				  	}				  	
				  	
				//save metadata
				  	Date saveDate = new Date();
				  	tp = new TextPanel("Results");
				  	tp.append("Saving date:	" + constants.dateTab.format(saveDate) + "	Analysis started:	" + constants.dateTab.format(startDate));
					tp.append("Processed image:	" + name[task]);
					if(selectRegion)	tp.append("Selected ROI file:	" + regionName);
					if(intensityCorrection)	tp.append("Intensity correction map:	" + corrName);
					if(!selectedSubstractVariant.equals(substractVariant[0])){
						tp.append("Substracted projection:	" + selectedSubstractVariant);
					}
					if(register)	tp.append("Registration file:	" + om.getFileName());
					tp.append("");
					tp.append("Datafile was generated by '"+PluginName+"', (\u00a9 2017-" + constants.dateY.format(new Date()) + ": Jan N Hansen (jan.hansen@uni-bonn.de))");
					tp.append("The plug-in '"+PluginName+"' is distributed in the hope that it will be useful,"
							+ " but WITHOUT ANY WARRANTY; without even the implied warranty of"
							+ " MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.");
					tp.append("The plug-in '"+PluginName+"' uses for registration the FIJI plugin MultiStackReg_ by Brad Busse.");
					tp.append("Plug-in version:	"+PluginVersion);		
				  	tp.saveAs(savePath + "_log.txt");
				  	
				//finish progress dialog
				  	if(selectedTaskVariant.equals(taskVariant[1])){
				  		imp.changes = false;
					  	imp.close();
				  	}else{
				  		imp.unlock();
				  	}
				  	
				  	progress.setBar(1.0);
					
					tasksSuccessfull [task] = true;
					
					System.gc();
				  	break running;		  	
			}//(end runnning)
//		}catch(Exception e){
//			progress.notifyMessage("Task " + (1+task) + "could not be processed... an error occured: " + e.getMessage(), ProgressDialog.ERROR);
//		}
		
		if(progress.isStopped()) break tasking;
		progress.moveTask(task);			
	}	
	
	boolean success = false;
	for(int task = 0; task < tasks; task++){
		if(tasksSuccessfull [task]){
			success = true;
		}
	}
	if(success)	done = true;
}

private void userCheck(ImagePlus imp){
	imp.show();
	new WaitForUserDialog ("check").show();
	imp.hide();
}

private static ImagePlus registerByFile(ImagePlus imp, String filePath, ProgressDialog progress){
	if(imp.getNChannels()>1)	IJ.error("registration not implemented for > 1 channel...");
	int c = 1;
	
	ImagePlus tempImp;
	for(int t = 0; t < imp.getNFrames(); t++){
		tempImp = IJ.createImage("temp imp", imp.getWidth(), imp.getHeight(), imp.getNSlices(), imp.getBitDepth());
		
		for(int x = 0; x < imp.getWidth(); x++){
			for(int y = 0; y < imp.getHeight(); y++){
				for(int z = 0; z < imp.getNSlices(); z++){
					int s = imp.getStackIndex(c+1, z+1, t+1)-1;
					tempImp.getStack().setVoxel(x, y, z, imp.getStack().getVoxel(x, y, s));
					tempImp.getStack().setSliceLabel("slice " + (z+1), (z+1));
				}						
			}
		}	
		
		tempImp.show();		
		
		IJ.run(tempImp, "MultiStackReg", "stack_1=[temp imp] action_1=[Load Transformation File] file_1=["+filePath+"]"
//		+ " stack_2=None action_2=Ignore file_2=[] transformation=[Rigid Body]");
		+ " stack_2=None action_2=Ignore file_2=[] transformation=[Scaled Rotation]");
		
		tempImp.hide();
		
		for(int x = 0; x < imp.getWidth(); x++){
			for(int y = 0; y < imp.getHeight(); y++){
				for(int z = 0; z < imp.getNSlices(); z++){
					int s = imp.getStackIndex(c+1, z+1, t+1)-1;
					imp.getStack().setVoxel(x, y, s, tempImp.getStack().getVoxel(x, y, z));
				}						
			}
		}
		
		tempImp.changes=false;
		tempImp.close();
		progress.addToBar(0.9*(1.0/(double)imp.getNFrames()));
		progress.updateBarText("registering t=" + (t+1) + "...");
	}
	System.gc();
	return imp;
}

/**
 * 1 <= i <= nr of slices
 * */
public static ImagePlus getSingleSlice(ImagePlus imp, int s){	
	ImagePlus newImp = IJ.createHyperStack("S" + s, imp.getWidth(), imp.getHeight(), imp.getNChannels(),1 , imp.getNFrames(), imp.getBitDepth());
	for(int c = 0; c < imp.getNChannels(); c++){
		for(int t = 0; t < imp.getNFrames(); t++){
			for(int x = 0; x < imp.getWidth(); x++){
				for(int y = 0; y < imp.getHeight(); y++){
					newImp.getStack().setVoxel(x, y, newImp.getStackIndex(c+1, 1, t+1)-1, 
							imp.getStack().getVoxel(x, y, imp.getStackIndex(c+1, s, t+1)-1));
				}
			}
		}	
	}	
	newImp.setCalibration(imp.getCalibration());
	return newImp;
}

public static void saveSlicesIndividually(ImagePlus imp, String path){
	ImagePlus plainImp;
	for(int s = 0; s < imp.getNSlices(); s++){
		plainImp = getSingleSlice(imp,s+1);
		IJ.saveAsTiff(plainImp, path + "_S" + constants.df0.format(s+1) + ".tif");
		plainImp.changes = false;
		plainImp.close();
	}
}


private static ImagePlus createICorrImage(ImagePlus imp, double [][] correctionMatrix){
	ImagePlus corrImp = imp.duplicate();
	for(int z = 0; z < corrImp.getStackSize(); z++){
		for(int x = 0; x < corrImp.getWidth(); x++){
			for(int y = 0; y < corrImp.getHeight(); y++){
				if(correctionMatrix[x][y] == 0.0){
					corrImp.getStack().setVoxel(x, y, z, 0.0);
				}else{
					corrImp.getStack().setVoxel(x, y, z, corrImp.getStack().getVoxel(x, y, z) * correctionMatrix [x][y]);
				}
			}
		}	
	}	
	return corrImp;
}

private static ImagePlus minIntensityProjection(ImagePlus imp){
	ImagePlus impMin = IJ.createImage("minimum projection", imp.getWidth(), imp.getHeight(), 1, imp.getBitDepth());
	
	int maxValue = (int) (Math.pow(2.0, imp.getBitDepth())-1);
	
	for(int x = 0; x < imp.getWidth(); x++){
		for(int y = 0; y < imp.getHeight(); y++){
			impMin.getStack().setVoxel(x, y, 0, maxValue);
			for(int s = 0; s < imp.getStackSize(); s++){
				if(imp.getStack().getVoxel(x, y, s) < impMin.getStack().getVoxel(x, y, 0)){
					impMin.getStack().setVoxel(x, y, 0, imp.getStack().getVoxel(x, y, s));
				}
			}
		}
	}		
	
	impMin.setCalibration(imp.getCalibration());
	return impMin;
}

private static ImagePlus substractedImage(ImagePlus imp, ImagePlus toSubstract){
	ImagePlus impSubstracted = imp.duplicate();
	double value;	
	
	for(int x = 0; x < imp.getWidth(); x++){
		for(int y = 0; y < imp.getHeight(); y++){
			for(int s = 0; s < imp.getStackSize(); s++){
				value = imp.getStack().getVoxel(x, y, s) - toSubstract.getStack().getVoxel(x, y, 0);
				if(value>0.0){
					impSubstracted.getStack().setVoxel(x, y, s, value);
				}else{
					impSubstracted.getStack().setVoxel(x, y, s, 0.0);
				}
			}
		}
	}	
	
//	impSubstracted.setCalibration(imp.getCalibration());
	return impSubstracted;
}
}
