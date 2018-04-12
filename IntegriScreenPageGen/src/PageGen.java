import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.json.JSONArray;
import org.json.JSONObject;

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
public class PageGen {
	
	public static void main(String[] args) throws IOException {
		
		String testName = "email";
		String jsonData = new String(Files.readAllBytes(new File(testName + ".json").toPath()), StandardCharsets.UTF_8);
		String htmlFile = new String(Files.readAllBytes(new File("template.txt").toPath()), StandardCharsets.UTF_8);
		
		FileWriter fw = new FileWriter(testName + ".html");
		
		JSONObject jObject = new JSONObject(jsonData);
		String pageName = jObject.getString("page");
		String ratioString = jObject.getString("ratio");
		String rarioHeight = ratioString.split(":")[0];
		String rarioWidth = ratioString.split(":")[1];
		
		System.out.println(htmlFile.contains("!!page_name!!"));
		htmlFile = htmlFile.replaceAll("!!page_name!!", pageName).replaceAll("!!height!!", rarioHeight).replaceAll("!!width!!", rarioWidth);
		
		
		String orderString = jObject.getString("order");
		String[] orderElements = orderString.split(":");
		JSONArray elements = jObject.getJSONArray("elements");
		
		
		/*
		 * Subject:<br>
  		<input type="text" name="subject" value="Bla Bla">
  		<br>

		 */
		StringBuffer elementHtmlString = new StringBuffer();
		for(int i = 0; i < elements.length(); i++)
		{
			JSONObject inObject = elements.getJSONObject(i);
			String id = inObject.getString("id");
			String type = inObject.getString("type");
			String editable = inObject.getString("editable");
			String initialValue = inObject.getString("initialvalue");
			System.out.println(initialValue);
			
			if(type.equalsIgnoreCase("textarea"))
				elementHtmlString.append(id + "<br>\n<" + type + " rows=\"10\" cols=\"50\">\n" + initialValue + "</textarea>\n");
							
			else
				elementHtmlString.append(id + "<br>\n<input type=" + type + " name =" + id + " value=" + initialValue + ">\n");
		}
		
		htmlFile = htmlFile.replaceAll("<form action=\"/action_page.php\">", "<form action=\"/action_page.php\">" + 
				elementHtmlString.toString());
		fw.write(htmlFile);
		fw.close();
		
	}

}
