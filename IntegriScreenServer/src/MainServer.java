

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Servlet implementation class MainServer
 */
@WebServlet("/MainServer")
@MultipartConfig

public class MainServer extends HttpServlet {

	String corePath = (new File("testFileName")).getAbsolutePath().replace("testFileName", "webapps/IntegriScreenServer/");
	Logger LOGGER = Logger.getLogger(MainServer.class.getName());
	
	public final String dataLocation = corePath + "data/";
	public final String generatedLocation = corePath + "generated/";
	public final String manifest = corePath + "generated/fileList.txt";	

	private static final long serialVersionUID = 1L;

	/**
	 * @see HttpServlet#HttpServlet()
	 */
	public static Set<String> fileString = new HashSet<>();
	public MainServer() {
		super();
		
		try {
			regenerateAllFiles();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private String regenerateAllFiles() throws IOException {
		System.out.println("Generate requiest received");

		
		File[] currentFiles = new File(dataLocation).listFiles();
		fileString = new HashSet<>();
		for(File file : currentFiles)
		{
			//System.out.println(file);
			if(file.getName().contains(".swp"))
				continue;
			else if(file.getName().contains(".json"))
				fileString.add(file.getName().replaceAll(".json", ""));
		}

		JSONObject outJson = new JSONObject();
		JSONArray jarray = new JSONArray();
		int counter = 0;
		for(String pageFileName:fileString)
		{
			String[] out = PageGen.pageGen(pageFileName, false, dataLocation, generatedLocation);
			System.out.println("Generated : " + pageFileName);

			JSONObject inJson = new JSONObject();
			inJson.put("page_id", out[0]);
			inJson.put("page_title", out[1]);
			inJson.put("json", out[2]);
			inJson.put("html", out[3]);
			inJson.put("unicorn", out[4]);
			jarray.put(counter++, inJson);
		}
		//get permission
		try
		{
			String s = null;
			//Process p = Runtime.getRuntime().exec("chmod 777 /home/dhara/tomcat/static/generated/*");        
			Process p = Runtime.getRuntime().exec("chmod 777 -R ../static/generated");        
			BufferedReader br = new BufferedReader(
					new InputStreamReader(p.getInputStream()));
			while ((s = br.readLine()) != null)
				System.out.println("line: " + s);

			br = new BufferedReader(
					new InputStreamReader(p.getErrorStream()));
			while ((s = br.readLine()) != null)
				System.out.println("line: " + s);

			p.waitFor();
			System.out.println ("exit: " + p.exitValue());
			p.destroy();
		}
		catch(Exception ex)
		{
			System.err.println("Things fucked up");
		}

		try
		{
			String s = null;
			//Process p = Runtime.getRuntime().exec("chmod 777 /home/dhara/tomcat/static/generated/*");        
			Process p = Runtime.getRuntime().exec("chmod 777 -R ../static/data");        
			BufferedReader br = new BufferedReader(
					new InputStreamReader(p.getInputStream()));
			while ((s = br.readLine()) != null)
				System.out.println("line: " + s);

			br = new BufferedReader(
					new InputStreamReader(p.getErrorStream()));
			while ((s = br.readLine()) != null)
				System.out.println("line: " + s);

			p.waitFor();
			System.out.println ("exit: " + p.exitValue());
			p.destroy();
		}
		catch(Exception ex)
		{
			System.err.println("Things fucked up");
		}
		outJson.put("response", jarray);

		FileWriter fw = new FileWriter(manifest);
		fw.append(outJson.toString(1));
		fw.close();
		
		return outJson.toString(1);
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		//no get method supported due to the multipart configuration nature of this Servlet
	}

	/**
	 * @throws IOException 
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		//input form
		String page_type = request.getParameter("page_type");

		//System.out.println(page_type);

		//show the list

		//page response from the browser
		if(page_type!= null && page_type.equalsIgnoreCase("input_form"))
		{
			System.out.println("Got a browser form");
			ProcessApplicationForm.processApplicationForm(request, response);
		}
		//page response from the phone
		else if(page_type!= null && page_type.equalsIgnoreCase("mobile_form"))
		{
			System.out.println("Got a mobile form");
			ProcessApplicationForm.processApplicationFormPhone(request, response);
		}
		//JSON upload and the automated generation of the pages and _specs configurations
		else
		{
			System.out.println(request.getParameter("generated"));


			boolean isGeneratedLoc =  request.getParameter("generated") == null ? false : true; 
			Part filePart = null;
			try {
				filePart = request.getPart("file");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} // Retrieves <input type="file" name="file">


			if(filePart != null)
			{
				// UPLOAD A FILE
				
				String fileName = Paths.get(filePart.getSubmittedFileName()).getFileName().toString(); // MSIE fix.
				InputStream fileContent = null;
				try {
					fileContent = filePart.getInputStream();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				StringWriter writer = new StringWriter();
				String fileData = null;
				if(fileName.contains(".zip"))
				{
					byte[] buffer = new byte[fileContent.available()];
					System.out.println("Found Zip with len: " + buffer.length);
					fileContent.read(buffer);
					OutputStream outStream = new FileOutputStream(dataLocation+ "/" + fileName);	
					outStream.write(buffer);
					outStream.close();
					
					//unzip

					Process p;
					String s;
					try 
					{
						String cmd = "unzip -o " + dataLocation + fileName + " -d " + dataLocation;
						System.out.println("Command : " + cmd);
						p = Runtime.getRuntime().exec(cmd);

						BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
						
						while ((s = br.readLine()) != null)
							System.out.println("line: " + s);
						
						p.waitFor();
						System.out.println ("exit value: " + p.exitValue());
						p.destroy();
					} 
					catch (Exception e) 
					{
						System.err.println("Shit happend!");
						e.printStackTrace();
					}		    		

				}

				else
				{
					try {
						IOUtils.copy(fileContent, writer, StandardCharsets.UTF_8);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					fileData = writer.toString();
				}


				FileWriter fw;
				try 
				{
					if(!fileName.contains(".zip"))
					{
						String loc = isGeneratedLoc ? generatedLocation + "/" + fileName : dataLocation + "/" + fileName;
						fw = new FileWriter(loc);
						fw.append(fileData);
						fw.close();
						System.out.println("FILENAME:" + fileName + " Received, uploaded at : " + loc);
					}
				} 
				catch (IOException e) 
				{
					System.err.println("User just pressed the upload without any file. Total moron!");
				}

				try {
					response.getWriter().append(regenerateAllFiles());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
			//System.out.println("File data : " + fileData);
			else
			{
				System.out.println("Generate request received");
				response.getWriter().append(regenerateAllFiles());

			}
		}
	}

}
