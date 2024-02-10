package com.cabolabs.openehr.rest.client

enum AuthTypeEnum {

   AUTH('auth'),
   TOKEN('token'),
   NONE('none')

   String value

   AuthTypeEnum(String value)
   {
      this.value = value
   }

   String toString()
   {
      this.value
   }

   static AuthTypeEnum fromString(String value)
   {
      for (AuthTypeEnum v : values())
      {
         if (v.getValue().equalsIgnoreCase(value)) return v;
      }
      throw new IllegalArgumentException();
   }
}