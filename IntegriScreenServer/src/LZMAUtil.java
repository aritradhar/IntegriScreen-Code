import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZ;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

//*************************************************************************************
//*********************************************************************************** *
//author Aritra Dhar 																* *
//PhD Researcher																  	* *
//ETH Zurich													   				    * *
//Zurich, Switzerland															    * *
//--------------------------------------------------------------------------------- * * 
///////////////////////////////////////////////// 									* *
//This program is meant to do world domination... 									* *
///////////////////////////////////////////////// 									* *
//*********************************************************************************** *
//*************************************************************************************

/**
 * @author Aritra
 *
 */
public class LZMAUtil {
	
	public static void LZMA_ZIP(String src, String dest) throws IOException
	{
		FileInputStream inFile = new FileInputStream(src);
		FileOutputStream outfile = new FileOutputStream(dest);

		LZMA2Options options = new LZMA2Options();

		options.setPreset(6); 

		XZOutputStream out = new XZOutputStream(outfile, options);

		byte[] buf = new byte[8192];
		int size;
		while ((size = inFile.read(buf)) != -1)
		   out.write(buf, 0, size);

		inFile.close();
		out.finish();
		out.close();
		outfile.close();
	}
	
	public static void LZMA_UNZIP(String src, String dest) throws IOException
	{
		FileInputStream inFile = new FileInputStream(src);
		FileOutputStream outfile = new FileOutputStream(dest);

		XZInputStream in = new XZInputStream(inFile);
		
		byte[] buf = new byte[8192];
		int size;
		while ((size = in.read(buf)) != -1)
		   outfile.write(buf, 0, size);

		inFile.close();
		in.close();
		outfile.close();

}

}
