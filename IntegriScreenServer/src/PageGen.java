import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.json.JSONArray;
import org.json.JSONObject;



public class PageGen {
	public static void main(String[] args) throws IOException {
		// This will load data/NAME.json as input and generate generated/NAME.html as output
		pageGen("email_1080_960", true, "WebContent/data", "WebContent/generated");
	}

	public static String[] pageGen(String pageFileName, boolean runLocally, String dataLocation, String generatedLocation) throws IOException {
//		String serverUrl = "http://tildem.inf.ethz.ch:8085";
		String serverUrl = "https://punk.cs.ox.ac.uk/IntegriScreenServer/";
		//String serverUrl = "http://idvm-infk-capkun01.inf.ethz.ch:8085";
				
		String jsonData = new String(Files.readAllBytes(new File(dataLocation + pageFileName + ".json").toPath()), StandardCharsets.UTF_8);
		String htmlFile = new String(Files.readAllBytes(new File(dataLocation + "template.txt").toPath()), StandardCharsets.UTF_8);

		System.out.println("--------------Opening File: " + pageFileName + ".html-----------------");


		FileWriter fw = new FileWriter(generatedLocation + pageFileName + ".html");
		FileWriter fwu = new FileWriter(generatedLocation + pageFileName + "_unicorn.html"); // "_unicorn files are those where the borders are colored"

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
		String fontsize = jObject.has("fontsize") ? jObject.getString("fontsize") : "12pt";


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
		
		elementHtmlString.append(String.format("<div style='height:%svh; width:%dvh; margin: 0 auto; position:relative;' id='frameBox'>\n", vspace, Math.round((double)vspaceInt * heightInt / widthInt)));
		// green border overlay
		elementHtmlString.append(String.format("<div style='border:%svh solid #00ff00; height:100%%; width:100%%; margin: 0 auto; position:absolute; box-sizing:border-box;' id='greenBox'></div>\n", border_thickness));

		int titleCounter = 0;

		String page_title_h2 = null;

		for(int i = 0; i < elements.length(); i++)
		{
			JSONObject inObject = elements.getJSONObject(i);
			String id = inObject.getString("id");
			String type = inObject.getString("type");
			//String editable = inObject.getString("editable");
			String initialValue = inObject.getString("initialvalue");
			String ulc_x = inObject.getString("ulc_x");
			String ulc_y = inObject.getString("ulc_y");

			double width = inObject.getDouble("width");
			double height = inObject.getDouble("height");

    		String elemFont = inObject.has("font") ? inObject.getString("font") : "inherit";
    		String elemFontSize = inObject.has("fontsize") ? inObject.getString("fontsize") : fontsize;
    		String letterSpacing = inObject.has("spacing") ? inObject.getString("spacing") : "normal";

    		String maxInputChars = inObject.has("maxlength") ? inObject.getString("maxlength") : "30";

			//System.out.println(id);


			if(type.equalsIgnoreCase("title"))
			{
				if(titleCounter == 1)
				{
					System.err.println("Error! More than 1 HTML title. Exiting");
					System.exit(1);
				}

				elementHtmlString.append(String.format("<h2 id='%s' name='%s' style='width:%s%%; height:%s%%; left:%s%%; top:%s%%; font-family:%s; font-size:%s !important;  position:absolute'>%s</h2>\n", id, id, width, height, ulc_x, ulc_y, elemFont, elemFontSize, initialValue));
				page_title_h2 = initialValue;

				//form action
				elementHtmlString.append(String.format("<form action='%s' method='post'  enctype='multipart/form-data'>", form_action));
				//hidden data
				elementHtmlString.append("<input type='hidden' id='attackLogs' name='attackLogs' value=''>");
				elementHtmlString.append("<input type='hidden' name='page_type' value='input_form' />");
				elementHtmlString.append(String.format("<input type='hidden' name='page_id' value='%s' />", pageName));
				titleCounter++;
			}

			else if(type.equalsIgnoreCase("textarea"))
			{
				elementHtmlString.append(String.format("<textarea name='%s' id='%s' style='left:%s%%; top:%s%%; position:absolute; height:%s%%; width:%s%%; font-family:%s; font-size:%s !important; letter-spacing:%s;'>%s</textarea>\n", 
						id, id, ulc_x, ulc_y, height, width, elemFont, elemFontSize, letterSpacing, initialValue));

			}

			else if(type.equalsIgnoreCase("textfield"))
			{

				elementHtmlString.append(String.format("<input type='text' name ='%s'  id='%s' value='%s' maxlength='%s' style='width:%s%%; height:%s%%; left:%s%%; top:%s%%; position:absolute; font-family:%s; font-size:%s !important; letter-spacing:%s;'>\n", 
						id, id, initialValue, maxInputChars, width, height, ulc_x, ulc_y, elemFont, elemFontSize, letterSpacing));
			}

			else if(type.equalsIgnoreCase("button"))
			{
				elementHtmlString.append(String.format("<input type='submit' value='%s' style='width:%s%%; height:%s%%;  font-family:%s; font-size:%s !important; left:%s%%; top:%s%%; position:absolute;'>", initialValue, width, height, elemFont, elemFontSize, ulc_x, ulc_y));
			}

			else if(type.equalsIgnoreCase("checkbox"))
			{
				elementHtmlString.append(String.format("<input type='hidden' id='%s_hidden' name='%s' value='False' >", id, id));
				elementHtmlString.append(String.format("<input type='checkbox' id='%s' name='%s' value='True' style='height:%s%%; width:%s%%; left:%s%%; top:%s%%; position:absolute;'>", id, id, height, width, ulc_x, ulc_y));
			}

			else if(type.equalsIgnoreCase("label"))
			{
				elementHtmlString.append(String.format("<label name='%s' id='%s' style='width:%s%%; height:%s%%; left:%s%%; top:%s%%; position:absolute; font-family:%s; font-size:%s !important; letter-spacing:%s;'>%s</label>\n", id, id, width, height, ulc_x, ulc_y, elemFont, elemFontSize, letterSpacing, initialValue));
			}

			divCounter++;


		}
		elementHtmlString.append("</form>\n</div>");

		htmlFile = htmlFile.replaceAll("!!font!!", global_font);
		htmlFile = htmlFile.replaceAll("!!letter_spacing!!", font_spacing);
		htmlFile = htmlFile.replaceAll("!!fontsize!!", fontsize);

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

		System.out.println("----------------Page generated----------------");

		String urlGenerated = serverUrl + "generated/";
		String urlData = serverUrl + "data/";
		
		return new String[] {pageFileName, page_title_h2, urlData + pageFileName + ".json", urlGenerated + pageFileName + ".html", urlGenerated + pageFileName + "_unicorn.html"};
	}

}
