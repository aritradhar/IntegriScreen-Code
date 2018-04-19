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
public class NewPageGen {
	
	
	public static void main(String[] args) throws IOException {
		pageGen("email");
	}
	
	
	public static void pageGen(String pageFileName) throws IOException
	{
		String jsonData = new String(Files.readAllBytes(new File(pageFileName + ".json").toPath()), StandardCharsets.UTF_8);
		String htmlFile = new String(Files.readAllBytes(new File("template.txt").toPath()), StandardCharsets.UTF_8);
		
		FileWriter fw = new FileWriter(pageFileName + ".html");
		
		JSONObject jObject = new JSONObject(jsonData);
		String pageName = jObject.getString("page");
		String ratioString = jObject.getString("ratio");
		String rarioHeight = ratioString.split(":")[0];
		int heightInt = Integer.parseInt(rarioHeight);
		String rarioWidth = ratioString.split(":")[1];
		int widthtInt = Integer.parseInt(rarioWidth);
		String form_action = jObject.getString("form_action");
		
		String border_thickness = jObject.getString("border_thickness");
		
		System.out.println(htmlFile.contains("!!page_name!!"));
		htmlFile = htmlFile.replaceAll("!!page_name!!", pageName).replaceAll("!!height!!", rarioHeight).replaceAll("!!width!!", rarioWidth);
		
	
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
		
		//border
		elementHtmlString.append("<div style=\"border:" + border_thickness + "vh solid #00ff00; height:" + vspace + "vh; width:" + 
					new Integer(vspaceInt/widthtInt * heightInt).toString() + "vh; margin: 0 auto; position:relative;\" id=\"frameBox\">\n");
		
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
				elementHtmlString.append("<textarea style=\"left:" + ulc_x + "%;top:" + ulc_y + "vh;position:absolute;height:" + height + "vh;width:" + width + "vh;\">" + initialValue + "</textarea>\n");
			
			}
			
			else if(type.equalsIgnoreCase("textfield"))
			{

				elementHtmlString.append("<input type=" + type + " name =" + id + " value=" + initialValue 
						+ " style=\"width:" + width + "%;left:" + ulc_x + "%;top:" + ulc_y + "vh;position:absolute\">\n");
			}
			
			else if(type.equalsIgnoreCase("label"))
			{

				elementHtmlString.append("<label style=\"width:" + width + "%;left:" + ulc_x + "%;top:" + ulc_y + "vh;position:absolute;\">" + initialValue + "</label>\n");
			}
			
			divCounter++;
			
			
		}
		elementHtmlString.append("</form>\n</div>");
		
		
		htmlFile = htmlFile.replaceAll("!!body!!", elementHtmlString.toString());


		
		fw.write(htmlFile);
		fw.close();
		
		System.out.println("Page generated");
		
	}

}
