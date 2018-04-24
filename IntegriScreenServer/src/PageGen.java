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
//Beautiful Life Specialist														  	* *
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
		// This will load email.json as input and generate email.html as output
		pageGen("email_1080_960");
		pageGen("email_1920_1080");
	}

	public static String pageGen(String pageFileName) throws IOException
	{
		String jsonData = new String(Files.readAllBytes(new File(MainServer.location + pageFileName + ".json").toPath()), StandardCharsets.UTF_8);
		String htmlFile = new String(Files.readAllBytes(new File(MainServer.location + "template.txt").toPath()), StandardCharsets.UTF_8);
		
		System.out.println("Opening File: " + MainServer.location + pageFileName + ".html");
		

		FileWriter fw = new FileWriter(MainServer.generatedLocation + pageFileName + ".html");
		FileWriter fwu = new FileWriter(MainServer.generatedLocation + pageFileName + "_unicorn.html"); // "_unicorn files are those where the borders are colored"
		FileWriter jsonSpecsFile = new FileWriter(MainServer.generatedLocation + pageFileName + "_specs.json");
		
		JSONObject jObject = new JSONObject(jsonData);
		String pageName = jObject.getString("page");
		String ratioString = jObject.getString("ratio");
		String ratioHeight = ratioString.split(":")[0];
		int heightInt = Integer.parseInt(ratioHeight);
		String ratioWidth = ratioString.split(":")[1];
		int widthInt = Integer.parseInt(ratioWidth);
		String form_action = jObject.getString("form_action");
		
		String border_thickness = jObject.getString("border_thickness");
		
		System.out.println(htmlFile.contains("!!page_name!!"));
		htmlFile = htmlFile.replaceAll("!!page_name!!", pageName).replaceAll("!!height!!", ratioHeight).replaceAll("!!width!!", ratioWidth);
		
	
		JSONArray elements = jObject.getJSONArray("elements");
		
		
		/*
		 * Subject:<br>
  		<input type="text" name="subject" value="Bla Bla">
  		<br>

		 */
		int divCounter = 1;
		StringBuffer elementHtmlString = new StringBuffer();
		
		String vspace = jObject.getString("vspace");
		int vspaceInt = Integer.parseInt(vspace);
		
		//container div
		elementHtmlString.append("<div style=\"height:" + vspace + "vh; width:" + String.valueOf(Math.round((double)vspaceInt * heightInt / widthInt)) + "vh; margin: 0 auto; position:relative;\" id=\"frameBox\">\n");
		// green border overlay
		elementHtmlString.append("<div style=\"border:" + border_thickness + "vh solid #00ff00; height:100%; width:100%; margin: 0 auto; position:absolute;box-sizing:border-box;\" id=\"greenBox\"></div>\n");
		
		int titleCounter = 0;
		
		for(int i = 0; i < elements.length(); i++)
		{
			JSONObject inObject = elements.getJSONObject(i);
			String id = inObject.getString("id");
			String type = inObject.getString("type");
			//String editable = inObject.getString("editable");
			String initialValue = inObject.getString("initialvalue");
			String ulc_x = inObject.getString("ulc_x");
			String ulc_y = inObject.getString("ulc_y");
			
			String width = inObject.getString("width");
			String height = inObject.getString("height");
			
			System.out.println(id);
			
					
			if(type.equalsIgnoreCase("title"))
			{
				if(titleCounter == 1)
				{
					System.err.println("Error! More than 1 HTML title. Exiting");
					System.exit(1);
				}
				
				elementHtmlString.append("<h2 style=\"width:" + width + "%;left:" + ulc_x +"%;top:" + ulc_y +"vh; position:absolute\">" + initialValue + "</h2>\n");
				
				//form action
				elementHtmlString.append("<form action=\""+ form_action + "\">");
				titleCounter++;
			}
			
			else if(type.equalsIgnoreCase("textarea"))
			{
				elementHtmlString.append("<textarea style=\"left:" + ulc_x + "%;top:" + ulc_y + "vh;position:absolute;height:" + height + "vh;width:" + width + "%;\">" + initialValue + "</textarea>\n");
			
			}
			
			else if(type.equalsIgnoreCase("textfield"))
			{

				elementHtmlString.append("<input type=" + type + " name =" + id + " value=\"" + initialValue 
						+ "\" style=\"width:" + width + "%;left:" + ulc_x + "%;top:" + ulc_y + "vh;position:absolute\">\n");
			}
			
			else if(type.equalsIgnoreCase("label"))
			{

				elementHtmlString.append("<label style=\"width:" + width + "%;left:" + ulc_x + "%;top:" + ulc_y + "vh;position:absolute;\">" + initialValue + "</label>\n");
			}
			
			divCounter++;
			
			
		    Double vert_mul = 100.0 / vspaceInt;
	        // Convert vspace and height from vh to percentage relative to the green border
			Double ulc_y_d = Double.parseDouble(ulc_y) * vert_mul;
			Double height_d = Double.parseDouble(height) * vert_mul;
			
			
			inObject.put("ulc_y", String.valueOf(ulc_y_d));
			inObject.put("height", String.valueOf(height_d));

		}
		elementHtmlString.append("</form>\n</div>");

		
		// ----- Generate the main .html file		
		htmlFile = htmlFile.replaceAll("!!body!!", elementHtmlString.toString());
		// Write out both files
		fw.write(htmlFile);    fw.close();
		
		// ----- Generate the _unicorn.html file		
		// Add the additional styling to make the UI elements colored
		htmlFile = htmlFile.replaceAll("<!--place-for-unicorn-styling-->",
				"label {background: #ffbdbd;} "
				+ "h1, h2, h3, h4, h5, h6 {background: #c9c9ff;}"
				+ "input {background: #e1f7d5;}");		
		fwu.write(htmlFile);  fwu.close();
		
		
		// --- Generate the _specs.json file
		jsonSpecsFile.write(jObject.toString(2)); jsonSpecsFile.close();
		
		System.out.println("Page generated");
		
		String urlName = MainServer.generatedLocation.replace("/home/dhara/tomcat/static", "http://tildem.inf.ethz.ch");
		return "Generated HTML => " + urlName + pageFileName + ".html" + "\n" + "Generated Uniocorn => " + urlName + pageFileName + "_unicorn.html" + 
				"\nJSON spec file => " + urlName + pageFileName + "_specs.json";
		
	}

}
