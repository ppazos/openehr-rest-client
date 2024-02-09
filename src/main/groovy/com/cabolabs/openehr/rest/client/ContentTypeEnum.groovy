package com.cabolabs.openehr.rest.client

enum ContentTypeEnum {

   XML('application/xml'),
   JSON('application/json')

   String value

   ContentTypeEnum(String value)
   {
      this.value = value
   }

   String toString()
   {
      this.value
   }
}