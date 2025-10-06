package com.cabolabs.openehr.rest.client.auth

class TokenAuth extends Authentication {

   String token

   TokenAuth(String token)
   {
      this.token = token
   }

   def apply(URLConnection request)
   {
      request.setRequestProperty("Authorization", "Bearer "+ this.token)
   }
}