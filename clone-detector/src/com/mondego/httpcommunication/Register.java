package com.mondego.httpcommunication;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.internal.util.collection.MultivaluedStringMap;

import com.mondego.indexbased.Daemon;

@Path("/register")
public class Register {
	/**
	 * register the client with a given manager
	 * @return
	 */
	@GET
    //@Produces(MediaType.TEXT_PLAIN)
    public static String sendRegistration() {
    	// TODO if the registration message was not successful, retry
    	Client client = ClientBuilder.newClient();
    	WebTarget webTarget = client.target("http://localhost:4567/register"); // TODO dynamic URI
    	MultivaluedMap formData = new MultivaluedStringMap();
    	formData.add("ip", "" + Daemon.getInstance().ip);
    	formData.add("port", "" + Daemon.getInstance().port);
    	Response response = webTarget
    			.request()
    			.post(Entity.form(formData));
    	System.out.println(response);
    	
    	Daemon test = Daemon.getInstance();
    	
        return "Sent.";
    }
}
