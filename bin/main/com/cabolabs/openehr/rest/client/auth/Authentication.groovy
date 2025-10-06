package com.cabolabs.openehr.rest.client.auth

abstract class Authentication {

   abstract def apply(URLConnection request)
}