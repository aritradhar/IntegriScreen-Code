import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;



public class ProcessApplicationForm {

	public static volatile Map<String, HashMap<String, String>> allBrowserResponses = new ConcurrentHashMap<>();
	
	public static final String pageDefaultLoc = "http://tildem.inf.ethz.ch/generated/";
	/**\
	 * page_id = title = filname
	 * @param request
	 * @param response
	 * @throws IOException
	 */
	public static void processApplicationForm(HttpServletRequest request, HttpServletResponse response) throws IOException
	{
		String pageID = request.getParameter("page_id");
		System.out.println("Page id : " + pageID);
		Enumeration<String> params = request.getParameterNames();
		
		HashMap<String, String> KVPair = new HashMap<>();
		
		System.out.println("--- Parameters from browser form ---");
		String atkMode = null;
		String atkType = null;
		while(params.hasMoreElements())
		{
			String key = params.nextElement();
			if (key.equals("atk_mode"))
				atkMode = request.getParameter(key);
			else if (key.equals("atk_type"))
				atkType = request.getParameter(key);
			else {
				KVPair.put(key, request.getParameter(key));
				System.out.println("Key: " + key + " | value: " + request.getParameter(key));				
			}
		}


		allBrowserResponses.put(pageID, KVPair);

		// response.getWriter().write("Response recorded: " + KVPair.toString());
		//	response.flushBuffer();
		String redirectURL = pageDefaultLoc + pageID + ".html?submitted=" + KVPair.toString();
		if (atkMode != null && atkType != null)
			redirectURL = redirectURL + "&atk_mode=" + atkMode + "&atk_type=" + atkType;
		
		response.sendRedirect(redirectURL);
	}
	
	public static void processApplicationFormPhone(HttpServletRequest request, HttpServletResponse response) throws IOException
	{

		String jsonString = IOUtils.toString(request.getInputStream(), StandardCharsets.UTF_8);
		JSONObject phoneDataJson = new JSONObject(jsonString);
		String page_id = phoneDataJson.getString("page_id");

		
		JSONObject outJson = new JSONObject();
		JSONArray failArray = new JSONArray();
			
		
		if(!allBrowserResponses.containsKey(page_id))
		{
			outJson.put("response", "__NULL__");
			outJson.put("info", "No data for page " + page_id + " submitted from the browser yet.");	
		}
		else
		{
			Set<String> allKeys = new HashSet<String>(phoneDataJson.keySet());
			HashMap<String, String> phoneResponse = new HashMap<String, String>();
			for(String key:allKeys) phoneResponse.put(key, phoneDataJson.getString(key));

			HashMap<String, String> browserResponse = allBrowserResponses.get(page_id);	
			
			// Create a union of all keys
			allKeys.addAll(browserResponse.keySet());
						
			
			// Go through all the keys that I know of
			for(String key: allKeys)
			{
				if (key.equals("page_id") || key.equals("page_type"))
					continue;

				String phoneVal = "__NULL__"; 
				String browserVal = "__NULL__";

				if (phoneResponse.containsKey(key)) phoneVal = phoneResponse.get(key);
				if (browserResponse.containsKey(key)) browserVal = browserResponse.get(key);
				
				if(!phoneVal.equals(browserVal))
				{
					JSONObject failJSON = new JSONObject();
					failJSON.put("elementid", key);
					failJSON.put("phone", phoneVal);
					failJSON.put("browser", browserVal);
					failArray.put(failJSON);
				}
 			}

			if(failArray.length() == 0) {
				outJson.put("response", "match");
			} else {
				outJson.put("response", "nomatch");
				outJson.put("diffs", failArray);
			}
		}
		
		// Output the generated JSON
		response.getWriter().write(outJson.toString(1));
		response.flushBuffer();
	}
}
