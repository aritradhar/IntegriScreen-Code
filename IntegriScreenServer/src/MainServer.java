

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

	public static final String location = "/home/dhara/tomcat/static/data/";
	public static final String generatedLocation = "/home/dhara/tomcat/static/generated/";
	public static final String manifest = "/home/dhara/tomcat/static/generated/fileList.txt";


	private static final long serialVersionUID = 1L;

	/**
	 * @see HttpServlet#HttpServlet()
	 */
	public static Set<String> fileString = new HashSet<>();
	public MainServer() {
		super();
		//create the list of json files
		File[] currentFiles = new File(location).listFiles();
		for(File file : currentFiles)
		{
			//System.out.println(file);
			if(file.getName().contains(".json"))
				fileString.add(file.getName().replaceAll(".json", ""));
		}
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
			Part filePart = null;
			try {
				filePart = request.getPart("file");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} // Retrieves <input type="file" name="file">


			if(filePart != null)
			{
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
					System.out.println("Found zip");
					byte[] buffer = new byte[fileContent.available()];
					System.out.println(buffer.length);
					fileContent.read(buffer);
					OutputStream outStream = new FileOutputStream(location+ "/" + fileName);	
					outStream.write(buffer);
					outStream.close();
					
					//unzip

					Process p;
					String s;
					try {
						String cmd = "unzip -o " + location + fileName + " -d " + location;
						System.out.println("Command : " + cmd);
						p = Runtime.getRuntime().exec(cmd);

						BufferedReader br = new BufferedReader(
								new InputStreamReader(p.getInputStream()));
						while ((s = br.readLine()) != null)
							System.out.println("line: " + s);
						p.waitFor();
						System.out.println ("exit: " + p.exitValue());
						p.destroy();
					} catch (Exception e) 
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
				try {
					if(!fileName.contains(".zip"))
					{
						fw = new FileWriter(location+ "/" + fileName);
						fw.append(fileData);
						fw.close();
					}
					System.out.println("FILENAME:" + fileName + " Received");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					System.err.println("User just pressed the upload without any file. Total moron!");
				}

				try {
					response.getWriter().append(fileName + " uploaded");
					response.sendRedirect("/upload.html");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}




			}
			//System.out.println("File data : " + fileData);
			else
			{
				System.out.println("Generate requiest received");

				File[] currentFiles = new File(location).listFiles();
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
					String[] out = PageGen.pageGen(pageFileName);
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

				response.getWriter().append(outJson.toString(1));

				FileWriter fw = new FileWriter(manifest);
				fw.append(outJson.toString(1));
				fw.close();

			}
		}
	}

}
