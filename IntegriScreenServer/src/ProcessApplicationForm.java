import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;


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
public class ProcessApplicationForm {
	
	public static Map<String, String> data = new HashMap<>(); 
	
	public static void processApplicationForm(HttpServletRequest request, HttpServletResponse response)
	{
		
	}
	
	public static void processApplicationFormPhone(HttpServletRequest request, HttpServletResponse response) throws IOException
	{
		String jsonString = IOUtils.toString(request.getInputStream(), StandardCharsets.UTF_8);
		
	}


}
