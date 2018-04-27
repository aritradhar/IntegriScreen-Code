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
		Enumeration<String> params = request.getParameterNames();
		
		HashMap<String, String> KVPair = new HashMap<>();
		
		while(params.hasMoreElements())
		{
			String key = params.nextElement();
			KVPair.put(key, request.getParameter(key));
		}
		
		
		data.put(pageID, KVPair);
		
		response.getWriter().write("Response recorded");
		response.flushBuffer();
	}
	
	public static void processApplicationFormPhone(HttpServletRequest request, HttpServletResponse response) throws IOException
	{
	
		StringBuffer responseBuffer = new StringBuffer();
		String jsonString = IOUtils.toString(request.getInputStream(), StandardCharsets.UTF_8);
		JSONObject jObject = new JSONObject(jsonString);
		String page_id = jObject.getString("page_id");
		
		boolean match = true;
		
		if(!data.containsKey(page_id))
		{
			response.getWriter().write("{\"Response\" : \"No data yet!\"");
		}
		else
		{
			Set<String> keySet = jObject.keySet();
			Iterator<String> keys = keySet.iterator();
			HashMap<String, String> params = data.get(page_id);
			
			while(keys.hasNext())
			{
				String key = keys.next();
				if(key.equals("page_id"))
					continue;
				
				if(params.containsKey(key))
				{
					if(!jObject.getString(key).equals(params.get(key)))
					{
						responseBuffer.append("Phone contains : " + key + " : " + jObject.getString(key) + " browser response contains " +  key + " : " + params.get(key) + "\n");
						match = false;
					}
				}
				else
				{
					responseBuffer.append("Phone contains : " + key + " but missing in browser response\n");
					match = false;
				}
			}
			
			if(match)
				responseBuffer.append("{\"Response\":\"Match\"");
			
			response.getWriter().write(responseBuffer.toString());
			response.flushBuffer();
		}
	}
}
