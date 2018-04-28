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


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;



/**
 * @author Aritra
 *
 */
public class ProcessApplicationForm {
	
	public static volatile Map<String, HashMap<String, String>> data = new ConcurrentHashMap<>();
	
	public static void processApplicationForm(HttpServletRequest request, HttpServletResponse response) throws IOException
	{
		String pageID = request.getParameter("page_id");
		System.out.println("Page id : " + pageID);
		Enumeration<String> params = request.getParameterNames();
		
		HashMap<String, String> KVPair = new HashMap<>();
		
		System.out.println("--- Parameters from browser form ---");
		while(params.hasMoreElements())
		{
			String key = params.nextElement();
			KVPair.put(key, request.getParameter(key));
			System.out.println("Key: " + key + " | value: " + request.getParameter(key));
		}
		System.out.println("--------");
		
		
		data.put(pageID, KVPair);
		
		response.getWriter().write("Response recorded");
		response.flushBuffer();
	}
	
	public static void processApplicationFormPhone(HttpServletRequest request, HttpServletResponse response) throws IOException
	{
	
		StringBuffer responseBuffer = new StringBuffer();
		String jsonString = IOUtils.toString(request.getInputStream(), StandardCharsets.UTF_8);
		JSONObject jObject = new JSONObject(jsonString);
		
		System.out.println("JSON String:" + jObject.toString(1));
		String page_id = jObject.getString("page_id");
		
		boolean match = true;
		
		if(!data.containsKey(page_id))
		{
			response.getWriter().write("{\"response\" : \"__NULL__\"}");
			response.flushBuffer();
			return;
		}
		else
		{
			JSONObject responseObject = new JSONObject();
			
			Set<String> keySet = jObject.keySet();
			Iterator<String> keys = keySet.iterator();
			HashMap<String, String> params = data.get(page_id);
			
			HashMap<String, String> jsonParams = new HashMap<>();
			
			for(String key:keySet)
				jsonParams.put(key, jObject.getString(key));
			
			
			JSONArray complainArray = new JSONArray();
			while(keys.hasNext())
			{
				String key = keys.next();
				if(key.equals("page_id") || key.equals("page_type"))
					continue;
				
				if(params.containsKey(key))
				{
					if(!jObject.getString(key).equals(params.get(key)))
					{
						JSONObject complainObject = new JSONObject();
						complainObject.put("key", key);
						complainObject.put("phone_value", jObject.getString(key));
						complainObject.put("browser_value", params.get(key));
						complainArray.put(complainObject);
						//responseBuffer.append("Phone contains : |" + key + "| : |" + jObject.getString(key) + "| browser response contains |" +  key + "| : |" + params.get(key) + "\n");
						match = false;
					}
				}
				else
				{
					JSONObject complainObject = new JSONObject();
					complainObject.put("key", key);
					complainObject.put("phone_value", jObject.getString(key));
					complainObject.put("browser_value", "__NULL__");
					complainArray.put(complainObject);
					//responseBuffer.append("Phone contains : |" + key + "| but missing in browser response\n");
					match = false;
				}
			}

			
			for(String browserKey:params.keySet())
			{
				if(browserKey.equals("page_type"))
					continue;
				if(!jsonParams.containsKey(browserKey))
				{
					JSONObject complainObject = new JSONObject();
					complainObject.put("key", browserKey);
					complainObject.put("phone_value", "__NULL__");
					complainObject.put("browser_value", params.get(browserKey));
					complainArray.put(complainObject);
					
					//responseBuffer.append("Browser response contains : |" + browserKey + "| but missing in phone response\n");
					match = false;
				}
			}
			
			
			if(match)
			{
				responseBuffer.append("{\"response\":\"match\"}");
				System.out.println("Page data removed from map");
				//data.remove(page_id);
				
				response.getWriter().write(responseBuffer.toString());
				response.flushBuffer();
			}
			else
			{
				responseObject.put("response", "notmatch");
				responseObject.put("reasons", complainArray);
				response.getWriter().write(responseObject.toString(1));
				response.flushBuffer();
			}
			
			
		}
	}
}
