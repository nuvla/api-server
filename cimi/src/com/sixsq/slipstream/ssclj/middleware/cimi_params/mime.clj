(ns com.sixsq.slipstream.ssclj.middleware.cimi-params.mime
  "Provides vars with information concerning the supported resource
  representations (mime-types).

  Normally, the desired representation is conveyed to the server via the
  'Accept' header, but may also be specified by the CIMI '$format' parameter.

  Currently json, xml, and edn are supported, with the default (and fallback)
  being json.")

(def accepted-mime-types
  "Map that associates the short name for the supported resource
  representation to the official mime-type string. (Note, these names are all
  lowercase.)"
  {"json" "application/json"
   "xml"  "application/xml"
   "edn"  "application/edn"})

(def accepted-formats
  "A set containing the short names for the supported resource
  representations.  Used to validate the '$format' query parameter
  if specified."
  (set (keys accepted-mime-types)))

