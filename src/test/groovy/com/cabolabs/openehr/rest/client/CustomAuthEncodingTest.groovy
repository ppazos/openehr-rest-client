package com.cabolabs.openehr.rest.client

import com.cabolabs.openehr.rest.client.auth.CustomAuth
import com.sun.net.httpserver.HttpServer
import spock.lang.Shared
import spock.lang.Specification

/**
 * The login of CustomAuth posts a form. A raw "+" in the email is read by the server as a space, and "&", "=", "%" or "+"
 * in the password break or change the form, so both values have to be URL-encoded.
 */
class CustomAuthEncodingTest extends Specification {

   @Shared HttpServer server
   @Shared String receivedBody
   @Shared String receivedContentType
   @Shared int status = 200

   def setupSpec()
   {
      server = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0)
      server.createContext('/auth', { exchange ->
         receivedBody = exchange.requestBody.getText('UTF-8')
         receivedContentType = exchange.requestHeaders.getFirst('Content-Type')
         byte[] bytes = (status == 200 ? '{"token": "abc.def"}' : '{"message": "auth failed"}').getBytes('UTF-8')
         exchange.responseHeaders.add('Content-Type', 'application/json')
         exchange.sendResponseHeaders(status, bytes.length)
         exchange.responseBody.withStream { it.write(bytes) }
      })
      server.start()
   }

   def cleanupSpec()
   {
      server.stop(0)
   }

   private Map parseForm(String body)
   {
      body.split('&').collectEntries {
         def kv = it.split('=', 2)
         [(URLDecoder.decode(kv[0], 'UTF-8')): URLDecoder.decode(kv[1], 'UTF-8')]
      }
   }

   def "the server decodes the same email and password that were configured"(String email, String password)
   {
      given:
      status = 200
      def auth = new CustomAuth("http://127.0.0.1:${server.address.port}/auth", email, password)
      def connection = new URL("http://127.0.0.1:${server.address.port}/other").openConnection()

      when:
      auth.apply(connection)
      def form = parseForm(receivedBody)

      then:
      receivedContentType == 'application/x-www-form-urlencoded'
      form.email == email
      form.password == password
      auth.token == 'abc.def'

      where:
      email                           | password
      'plain@example.com'             | 'plainpass'
      'cabolabs+cuafhir@gmail.com'    | 'secret'
      'user@example.com'              | "a?b!c'd,e"
      'user@example.com'              | 'p&ss=w%rd+x#y'
      'usér@example.com'              | 'clavé ñ'
   }
}
