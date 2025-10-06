package com.cabolabs.openehr.rest.client

enum PreferEnum {

   REPRESENTATION('return=representation'),
   MINIMAL('return=minimal')

   String value

   PreferEnum(String value)
   {
      this.value = value
   }

   String toString()
   {
      this.value
   }
}