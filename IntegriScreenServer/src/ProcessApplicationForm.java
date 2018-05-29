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
		String redirectURL = pageDefaultLoc + pageID + ".html?submitted=" + (new JSONObject(KVPair)).toString();
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


				// At the moment, we are using approximate matching, ignoring whitespace and cases
				// if(!phoneVal.equals(browserVal))
				if (almostIdenticalString(phoneVal, browserVal, false))
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



	// ======================================================
	// This code is identical as the Java mobile app code!

	static boolean[][] similar = new boolean[256][256];
	// We use this function for now to allow for some slack in how well does OCR work, since e.g. i and j are often confused with the current font.
	public static boolean similarChar(char A, char B, boolean allowMinorMismatch) {
		if (A >= 256 || B >= 256) return (A == B);

		for(int i = 0; i < 256; ++i)
			for(int j = 0; j < 256; ++j)
				similar[i][j] = (i == j);

		if (allowMinorMismatch) { // this allows a bit of "slack" in comparison, by saying it's fine if e.g. i and j get replaced
			similar['i']['j'] = similar['j']['i'] = true;
			similar['8']['b'] = similar['b']['8'] = true;
			similar['0']['8'] = similar['8']['0'] = true;
			similar['0']['o'] = similar['o']['0'] = true;
		}

		return similar[A][B];
	}

	// This method compares two strings, but loosely: ignoring whitesace and punctuation, and allowing that some characters are "similar"
	public static boolean almostIdenticalString(String A, String B, boolean allowMinorCharMismatch) {
		A = OCRTrim(A);
		B = OCRTrim(B);

		if (A.length() != B.length()) return false;
		for(int i = 0; i < A.length(); ++i)
			if (!similarChar(A.charAt(i), B.charAt(i), allowMinorCharMismatch))
				return false;

		return true;
	}

	public static String OCRTrim(String s) {
		String punctuationRegex = "[.,:!?\\-]";
		s = s.toLowerCase().replaceAll("\\s+","");
		s = s.replaceAll(punctuationRegex,"");
		return s;
	}

	// ======================================================
}
