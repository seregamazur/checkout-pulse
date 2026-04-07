local stockKey = KEYS[1]
local reservedKey = KEYS[2]
local qty = tonumber(ARGV[1])

-- 1. Check if stock is already present
local stock = redis.call('GET', stockKey)
-- Cache miss. Go pull from DB inventory and put it to cache first
if not stock then
    return -1 --
end

stock = tonumber(stock)

local reserved = tonumber(redis.call('GET', reservedKey) or '0')

-- Is there enough stock to allow to put product?
if (stock - reserved) >= qty then
    -- Atomic increment
    redis.call('INCRBY', reservedKey, qty)
    return 1 -- Success
else
    return 0 -- Not enough stock
end
