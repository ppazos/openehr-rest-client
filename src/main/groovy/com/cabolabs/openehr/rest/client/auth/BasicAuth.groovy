package com.cabolabs.openehr.rest.client.auth

class BasicAuth extends Authentication {

   String username
   String password
   String token

   BasicAuth(String username, String password)
   {
      this.username = username
      this.password = password
      this.token = (username +':'+ password).bytes.encodeBase64().toString()
   }

   def apply(URLConnection request)
   {
      request.setRequestProperty("Authorization", "Basic "+ this.token)
   }
}