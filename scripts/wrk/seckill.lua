local tokens = {}
local token_file = os.getenv("TOKEN_FILE") or "scripts/jmeter/tokens.example.csv"
local seckill_goods_id = os.getenv("SECKILL_GOODS_ID") or "1"

for line in io.lines(token_file) do
  if line ~= "token" and line ~= "" then
    table.insert(tokens, line)
  end
end

local counter = 1

request = function()
  if #tokens == 0 then
    error("no tokens loaded")
  end

  local token = tokens[counter]
  counter = counter + 1
  if counter > #tokens then
    counter = 1
  end

  wrk.method = "POST"
  wrk.headers["Authorization"] = token
  wrk.headers["Content-Type"] = "application/json"
  return wrk.format(nil, "/api/seckill/do/" .. seckill_goods_id)
end
