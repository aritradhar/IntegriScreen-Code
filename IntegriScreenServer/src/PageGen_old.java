import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.json.JSONArray;
import org.json.JSONObject;

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

		JSONObject jObject = new JSONObject(jsonData);
		String pageName = jObject.getString("page_id");
		String ratioString = jObject.getString("ratio");
		String ratioHeight = ratioString.split(":")[0];
		int heightInt = Integer.parseInt(ratioHeight);
		String ratioWidth = ratioString.split(":")[1];
		int widthInt = Integer.parseInt(ratioWidth);
		String form_action = jObject.getString("form_action");

		String border_thickness = jObject.getString("border_thickness");

		String global_font = jObject.getString("font_family");
		String font_spacing = jObject.getString("letter_spacing");


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

    		String elemFont = inObject.has("font") ? inObject.getString("font") : "inherit";
    		String letterSpacing = inObject.has("spacing") ? inObject.getString("spacing") : "normal";

    		String maxInputChars = inObject.has("maxlength") ? inObject.getString("maxlength") : "30";

			System.out.println(id);


			if(type.equalsIgnoreCase("title"))
			{
				if(titleCounter == 1)
				{
					System.err.println("Error! More than 1 HTML title. Exiting");
					System.exit(1);
				}

				elementHtmlString.append("<h2 style=\"width:" + width + "%;height:" + height + "%;left:" + ulc_x +"%;top:" + ulc_y +"%; position:absolute\">" + initialValue + "</h2>\n");

				//form action
				elementHtmlString.append("<form action=\""+ form_action + "\" method=\"post\"  enctype=\"multipart/form-data\">");
				//hidden data
				elementHtmlString.append("<input type=\"hidden\" name=\"" + "page_type" + "\" value=\""+ "input_form" + "\"/>");
				elementHtmlString.append("<input type=\"hidden\" name=\"" + "page_id" + "\" value=\""+ pageName + "\"/>");
				titleCounter++;
			}

			else if(type.equalsIgnoreCase("textarea"))
			{
				elementHtmlString.append("<textarea style=\"left:" + ulc_x + "%;top:" + ulc_y + "%;position:absolute;height:" + height + "%;width:" + width + "%;font-family:" + elemFont + ";letter-spacing:"+ letterSpacing +";\">" + initialValue + "</textarea>\n");

			}

			else if(type.equalsIgnoreCase("textfield"))
			{

				elementHtmlString.append("<input type=" + type + " name =" + id + " value=\"" + initialValue
						+ "\" maxlength=\"" + maxInputChars + "\" style=\"width:" + width + "%;height:" + height + "%left:" + ulc_x + "%;top:" + ulc_y + "%;position:absolute;font-family:" + elemFont + ";letter-spacing:"+ letterSpacing +";\">\n");
			}

			else if(type.equalsIgnoreCase("button"))
			{
				elementHtmlString.append("<input type=\"submit\" value=\"" + initialValue + "\" style=\"width:" + width + "%;height:" + height + "%;left:" + ulc_x + "%;top:" + ulc_y + "%;position:absolute;\">");
			}

			else if(type.equalsIgnoreCase("checkbox"))
			{
				elementHtmlString.append("<input type=\"checkbox\"  name =\"" + id + "\" value=\"" + id + "\" style=\"height:" + height + "%;width:" + width + "%;left:" + ulc_x + "%;top:" + ulc_y + "%;position:absolute;\">");
			}

			else if(type.equalsIgnoreCase("label"))
			{
				elementHtmlString.append("<label style=\"width:" + width + "%;height:" + height + "%;left:" + ulc_x + "%;top:" + ulc_y + "%;position:absolute;font-family:" + elemFont + ";letter-spacing:"+ letterSpacing +";\">" + initialValue + "</label>\n");
			}

			divCounter++;


		}
		elementHtmlString.append("</form>\n</div>");

		htmlFile = htmlFile.replaceAll("!!font!!", global_font);
		htmlFile = htmlFile.replaceAll("!!letter_spacing!!", font_spacing);

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

		System.out.println("Page generated");

		String urlName = MainServer.generatedLocation.replace("/home/dhara/tomcat/static", "http://tildem.inf.ethz.ch");
		return "Generated HTML => " + urlName + pageFileName + ".html" + "\n" + "Generated Uniocorn => " + urlName + pageFileName + "_unicorn.html" +
				"\nJSON spec file => " + urlName + pageFileName + ".json";

	}

}
