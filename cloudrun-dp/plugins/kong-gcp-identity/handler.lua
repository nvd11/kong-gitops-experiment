local http = require "resty.http"
local kong = kong

local GCPIdentityHandler = {
  PRIORITY = 900,
  VERSION = "1.0.0",
}

function GCPIdentityHandler:access(config)
  local aud = config.audience
  local metadata_url = "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/identity?audience=" .. aud
  
  local httpc = http.new()
  local res, err = httpc:request_uri(metadata_url, {
    method = "GET",
    headers = {
      ["Metadata-Flavor"] = "Google",
    }
  })

  if not res then
    kong.log.err("Failed to fetch Google Identity Token: ", err)
    return kong.response.exit(500, { message = "Internal Gateway Error (Identity)" })
  end

  if res.status ~= 200 then
    kong.log.err("GCP Metadata returned status: ", res.status)
    return kong.response.exit(500, { message = "Failed to obtain identity token" })
  end

  local id_token = res.body
  -- 自动为发往 Cloud Run 的请求附加身份凭证
  kong.service.request.set_header("Authorization", "Bearer " .. id_token)
end

return GCPIdentityHandler
