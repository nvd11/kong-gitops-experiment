local typedefs = require "kong.db.schema.typedefs"

return {
  name = "kong-gcp-identity",
  fields = {
    { consumer = typedefs.no_consumer },
    { config = {
        type = "record",
        fields = {
          { audience = { type = "string", required = true } },
        },
      },
    },
  },
}
