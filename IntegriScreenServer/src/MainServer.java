

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
		
		System.out.println(page_type);
		
		//page response from the browser
		if(page_type!= null && page_type.equalsIgnoreCase("input_form"))
		{
			System.out.println("Here");
			ProcessApplicationForm.processApplicationForm(request, response);
		}
		//page response from the phone
		else if(page_type!= null && page_type.equalsIgnoreCase("mobile_form"))
		{
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
				try {
					IOUtils.copy(fileContent, writer, StandardCharsets.UTF_8);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				String fileData = writer.toString();

				System.out.println("FILENAME:" + fileName + " Received");

				FileWriter fw;
				try {
					fw = new FileWriter(location+ "/" + fileName);
					fw.append(fileData);
					fw.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
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

				StringBuffer logs = new StringBuffer();
				JSONObject outJson = new JSONObject();
				JSONArray jarray = new JSONArray();
				int counter = 0;
				for(String pageFileName:fileString)
				{
					String[] out = PageGen.pageGen(pageFileName);
					System.out.println("Generated : " + pageFileName);
					
					JSONObject inJson = new JSONObject();
					inJson.put("form_id", out[0]);
					inJson.put("json", out[1]);
					inJson.put("html", out[2]);
					inJson.put("unicorn", out[3]);
					jarray.put(counter++, inJson);
				}
				outJson.put("response", jarray);

				response.getWriter().append(outJson.toString(1));
			}
		}
	}

}
