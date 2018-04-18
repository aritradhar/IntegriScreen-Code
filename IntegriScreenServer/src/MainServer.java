

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet implementation class MainServer
 */
@WebServlet("/MainServer")
public class MainServer extends HttpServlet {
	private static final long serialVersionUID = 1L;

	/**
	 * @see HttpServlet#HttpServlet()
	 */
	public static Set<String> fileString = new HashSet<>();
	public MainServer() {
		super();
		//create the list of json files
		File[] currentFiles = new File(".").listFiles();
		for(File file : currentFiles)
		{
			System.out.println(file);
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
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		doGet(request, response);
	}

}
