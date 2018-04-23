

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

/**
 * Servlet implementation class MainServer
 */
@WebServlet("/MainServer")
@MultipartConfig

public class MainServer extends HttpServlet {

	public static String location = "/home/dhara/tomcat/static/data";
	public static String generatedLocation = "/home/dhara/tomcat/static/generated";
	
	
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
		// TODO Auto-generated method stub
		String pageId = request.getParameter("page");


		String file = request.getParameter("file"); // Retrieves <input type="text" name="description">
		Part filePart = request.getPart("file"); // Retrieves <input type="file" name="file">
		//String fileName = Paths.get(filePart.getSubmittedFileName()).getFileName().toString(); // MSIE fix.
		//InputStream fileContent = filePart.getInputStream();

		System.out.println(file);


		if(pageId == null)
			response.getWriter().append("ERROR!");

		else
		{
			if(!fileString.contains(pageId))
				response.getWriter().append("The specification for the requested page does not exist");

			else
			{
				String jsonData = new String(Files.readAllBytes(new File(pageId + ".json").toPath()), StandardCharsets.UTF_8);
				response.getWriter().append(jsonData);
			}
		}
	}

	/**
	 * @throws IOException 
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub

		//String flag =  request.getParameter("flag");

		//if(flag.equals("upload"))
		//	System.out.println("UPLOAD!!");


		Part filePart = null;
		try {
			filePart = request.getPart("file");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} // Retrieves <input type="file" name="file">
		String fileName = Paths.get(filePart.getSubmittedFileName()).getFileName().toString(); // MSIE fix.

		if(fileName != null)
		{
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
			for(File file : currentFiles)
			{
				//System.out.println(file);
				if(file.getName().contains(".json"))
					fileString.add(file.getName().replaceAll(".json", ""));
			}
			
			StringBuffer logs = new StringBuffer();
			for(String pageFileName:fileString)
			{
				String out = PageGen.pageGen(pageFileName);
				System.out.println("Generated : " + pageFileName);
				logs.append(out + "\n");
			}
			
			response.getWriter().append(logs.toString());
		}
	}

}
