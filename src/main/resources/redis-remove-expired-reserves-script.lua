local now = ARGV[1]
local queueKey = 'cart:cleanup:queue'

-- 1. Get expired carts of user IDs
local cartsToClean = redis.call('ZRANGEBYSCORE', queueKey, 0, now)

if #cartsToClean > 0 then
    for i = 1, #cartsToClean do
        local userId = cartsToClean[i]
        local cartKey = 'cart:' .. userId

        -- 2. Get cart
        local raw_cart = redis.call('HGETALL', cartKey)

        if #raw_cart > 0 then
            for j = 1, #raw_cart, 2 do
                local productId = raw_cart[j]
                local qty = raw_cart[j + 1]
                local reservedKey = 'reserved:' .. productId

                -- 3. Return product to inventory (decrement reserved key)
                redis.call('DECRBY', reservedKey, qty)
            end
        end

        -- 4. Delete cart
        redis.call('DEL', cartKey)

        -- 5. Delete cart from queue
        redis.call('ZREM', queueKey, userId)
    end
end

return #cartsToClean