function readAll(file)
    local f = io.open(file, "rb")
    if f~=nil then
      local content = f:read("*all")
      f:close()
      return content
    else
      io.write("File not found " .. file)
      return false
    end
end

local name = ngx.var.arg_name or "Anonymous"
local contents =  readAll("/home/ubuntu/oss.io/oss.io__p__" .. ngx.var.usr .. "__" .. ngx.var.repo )

if contents ~= false then
  ngx.say('<!-- Hello,',name, '!-->', contents)
else
  ngx.exit(404)
end
