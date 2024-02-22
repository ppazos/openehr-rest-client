package com.cabolabs.openehr.rest.client.auth

import groovy.json.JsonSlurper

class CustomAuth extends Authentication {

   String endpoint
   String username
   String password
   String token
   Object response

   CustomAuth(String endpoint, String username, String password)
   {
      this.endpoint = endpoint
      this.username = username
      this.password = password
   }

   def apply(URLConnection request)
   {
      if (!token)
      {
         doAuth()
      }

      request.setRequestProperty("Authorization", "Bearer "+ this.token)
   }

   private boolean doAuth()
   {
      def req = new URL(this.endpoint).openConnection()
      def response_body = ''

      req.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
      req.setRequestProperty("Accept",       "application/json")

      req.setRequestMethod("POST")
      req.setDoOutput(true)

      String body = "username=${this.username}&password=${this.password}"
      req.getOutputStream().write(body.getBytes("UTF-8"));
      //req.setRequestProperty("Content-Length", Integer.toString(body_bytes.length))

      try
      {
         // this throws an exception if the response status code is not 2xx
         response_body = req.getInputStream().getText()
      }
      catch (java.net.ConnectException e)
      {
         throw new Exception("There was a problem connecting with to ${this.endpoint}")
      }
      catch (Exception e)
      {
         // for 4xx errors, the server will return a JSON payload error
         response_body = req.getErrorStream().getText()
      }

      // NOTE: it expects a JSON in the response!
      def json_parser = new JsonSlurper()
      this.response = json_parser.parseText(response_body)

      def status = req.getResponseCode()

      if (status.equals(200))
      {
         // expects a JSON with a key 'token'
         this.token = this.response.token

         //println this.token
         return true
      }
      else
      {
         return false
      }
   }
}