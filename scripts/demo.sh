#!/usr/bin/env bash
# End-to-end demo of every feature in the system, driven entirely through the live HTTP API —
# nothing here is a unit test double or an in-process call. Run against a freshly started app
# (default H2 profile, or `--spring.profiles.active=demo` for a faster sweep interval so the
# ops/sweep-now section has something to actually converge).
#
# Usage:
#   ./mvnw spring-boot:run                              # terminal 1
#   ./scripts/demo.sh                                    # terminal 2
#
# Every section is numbered and echoed before it runs, so this doubles as a spoken-word script
# for the video: read the section header aloud, then let the curl output speak for itself.
#
# Not `set -e`: several calls are EXPECTED to fail (a duplicate hold, a declined payment, a
# reused discount code past its cap) and the script must keep going to show the next section.

BASE="${BASE:-http://localhost:8080/api/v1}"

# ---------- helpers ----------
section() { echo; echo "================================================================"; echo "  $1"; echo "================================================================"; }
step()    { echo; echo "--- $1 ---"; }
jget()    { python3 -c "import sys,json;d=json.load(sys.stdin);print(d$1)" 2>/dev/null; }
must_be_up() {
  if ! curl -s -o /dev/null -w "%{http_code}" "$BASE/cities" | grep -q "200"; then
    echo "ERROR: app is not reachable at $BASE. Start it first: ./mvnw spring-boot:run"
    exit 1
  fi
}

must_be_up

# =====================================================================================
section "0. Health check + API docs"
# =====================================================================================
step "Swagger UI / OpenAPI (reviewable without running curl at all)"
echo "  http://localhost:8080/swagger-ui.html"
echo "  http://localhost:8080/v3/api-docs"

# =====================================================================================
section "1. Admin: catalog setup (cities, theaters, screens, bulk seat layout, movies)"
# =====================================================================================
step "Admin login"
ADMIN_TOKEN=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
  -d '{"email":"admin@cineagent.dev","password":"Admin@123"}' | jget "['token']")
echo "admin token: ${ADMIN_TOKEN:0:24}..."

step "Create city"
CITY_ID=$(curl -s -X POST "$BASE/admin/cities" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Bengaluru","state":"Karnataka","country":"IN","timezone":"Asia/Kolkata"}' | jget "['id']")
echo "city: $CITY_ID"

step "Create theater"
THEATER_ID=$(curl -s -X POST "$BASE/admin/theaters" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"cityId\":$CITY_ID,\"name\":\"PVR Forum\",\"address\":\"Koramangala\"}" | jget "['id']")
echo "theater: $THEATER_ID"

step "Create screen"
SCREEN_ID=$(curl -s -X POST "$BASE/admin/screens" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"theaterId\":$THEATER_ID,\"name\":\"Screen 1\"}" | jget "['id']")
echo "screen: $SCREEN_ID"

step "Bulk seat layout: rows A-C x 10 REGULAR, row D x 8 PREMIUM (one POST, not 38)"
curl -s -X POST "$BASE/admin/screens/$SCREEN_ID/seats/bulk" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"rows":[
        {"rowLabel":"A","seatCount":10,"category":"REGULAR"},
        {"rowLabel":"B","seatCount":10,"category":"REGULAR"},
        {"rowLabel":"C","seatCount":10,"category":"REGULAR"},
        {"rowLabel":"D","seatCount":8,"category":"PREMIUM"}
      ]}' | python3 -c "import sys,json;print(len(json.load(sys.stdin)),'seats created')"

step "Create movie"
MOVIE_ID=$(curl -s -X POST "$BASE/admin/movies" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"Interstellar","language":"en","durationMinutes":169,"certification":"U","synopsis":"A team of explorers travel through a wormhole.","releaseDate":"2014-11-07"}' | jget "['id']")
echo "movie: $MOVIE_ID"

# =====================================================================================
section "2. Admin: create a show (per-category pricing, server-derived city, overlap check)"
# =====================================================================================
STARTS=$(python3 -c "import datetime;print((datetime.datetime.utcnow()+datetime.timedelta(days=2)).isoformat()+'Z')")
ENDS=$(python3 -c "import datetime;print((datetime.datetime.utcnow()+datetime.timedelta(days=2,hours=3)).isoformat()+'Z')")
SHOW=$(curl -s -X POST "$BASE/admin/shows" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"screenId\":$SCREEN_ID,\"movieId\":$MOVIE_ID,\"startsAt\":\"$STARTS\",\"endsAt\":\"$ENDS\",
       \"prices\":[{\"category\":\"REGULAR\",\"baseAmount\":250.00},{\"category\":\"PREMIUM\",\"baseAmount\":400.00}]}")
echo "$SHOW" | python3 -m json.tool
SHOW_ID=$(echo "$SHOW" | jget "['id']")

step "Overlap guard: try to double-book the same screen at the same time -> expect 409 SCREEN_OVERLAP"
curl -s -w "\nHTTP:%{http_code}\n" -X POST "$BASE/admin/shows" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"screenId\":$SCREEN_ID,\"movieId\":$MOVIE_ID,\"startsAt\":\"$STARTS\",\"endsAt\":\"$ENDS\",
       \"prices\":[{\"category\":\"REGULAR\",\"baseAmount\":250.00},{\"category\":\"PREMIUM\",\"baseAmount\":400.00}]}"

# =====================================================================================
section "3. Admin: discount codes and refund policies"
# =====================================================================================
VF=$(python3 -c "import datetime;print(datetime.datetime.utcnow().isoformat()+'Z')")
VU=$(python3 -c "import datetime;print((datetime.datetime.utcnow()+datetime.timedelta(days=30)).isoformat()+'Z')")

step "Create a 10%-off, single-use discount code"
curl -s -X POST "$BASE/admin/discounts" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"code\":\"WELCOME10\",\"discountType\":\"PERCENTAGE\",\"value\":10,\"maxRedemptions\":1,\"validFrom\":\"$VF\",\"validUntil\":\"$VU\"}"
echo

step "Create a flat-amount code to show the discount TYPE axis (not just percentage)"
curl -s -X POST "$BASE/admin/discounts" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"code\":\"FLAT50\",\"discountType\":\"FLAT\",\"value\":50,\"maxRedemptions\":100,\"validFrom\":\"$VF\",\"validUntil\":\"$VU\"}"
echo

step "The GLOBAL refund-policy tiers are seeded by V101 (100% >=24h, 50% >=6h, 0% otherwise)."
step "Add a city-scoped override to show most-specific-scope-wins resolution"
curl -s -X POST "$BASE/admin/refund-policies" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"scopeType\":\"CITY\",\"scopeId\":$CITY_ID,\"minMinutesBeforeShow\":1440,\"refundPercentage\":100}"
echo

# =====================================================================================
section "4. Customer: register, browse, seat map"
# =====================================================================================
step "Register two customers (buyer1 wins the contention demo later, buyer2 loses)"
BUYER1_TOKEN=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d '{"email":"buyer1@demo.local","password":"password123","fullName":"Buyer One"}' | jget "['token']")
BUYER2_TOKEN=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d '{"email":"buyer2@demo.local","password":"password123","fullName":"Buyer Two"}' | jget "['token']")
echo "buyer1: ${BUYER1_TOKEN:0:24}...   buyer2: ${BUYER2_TOKEN:0:24}..."

step "GET /auth/me"
curl -s "$BASE/auth/me" -H "Authorization: Bearer $BUYER1_TOKEN"
echo

step "Browse shows, unauthenticated (public endpoint)"
curl -s "$BASE/shows?cityId=$CITY_ID" | python3 -c "import sys,json;print(json.load(sys.stdin)['totalElements'],'shows in Bengaluru')"

step "Seat map with per-seat price (public endpoint)"
SEATS_JSON=$(curl -s "$BASE/shows/$SHOW_ID/seats")
echo "$SEATS_JSON" | python3 -c "import sys,json;d=json.load(sys.stdin);print(len(d),'seats,','first:',d[0])"

# =====================================================================================
section "5. Customer: seat holds (acquire, extend, release)"
# =====================================================================================
SEAT_A1=$(echo "$SEATS_JSON" | python3 -c "import sys,json;print(json.load(sys.stdin)[0]['showSeatId'])")
SEAT_A2=$(echo "$SEATS_JSON" | python3 -c "import sys,json;print(json.load(sys.stdin)[1]['showSeatId'])")
SEAT_A3=$(echo "$SEATS_JSON" | python3 -c "import sys,json;print(json.load(sys.stdin)[2]['showSeatId'])")

step "Hold seats A1, A2 as buyer1"
HOLD=$(curl -s -X POST "$BASE/shows/$SHOW_ID/holds" -H "Authorization: Bearer $BUYER1_TOKEN" -H "Content-Type: application/json" \
  -d "{\"seatIds\":[$SEAT_A1,$SEAT_A2]}")
echo "$HOLD"
HOLD_ID=$(echo "$HOLD" | jget "['holdId']")

step "buyer2 tries to hold the SAME seat -> expect 409 SEAT_UNAVAILABLE with conflictingSeats"
curl -s -w "\nHTTP:%{http_code}\n" -X POST "$BASE/shows/$SHOW_ID/holds" -H "Authorization: Bearer $BUYER2_TOKEN" -H "Content-Type: application/json" \
  -d "{\"seatIds\":[$SEAT_A1]}"

step "Extend the hold (resets the TTL clock)"
curl -s -X POST "$BASE/holds/$HOLD_ID/extend" -H "Authorization: Bearer $BUYER1_TOKEN"
echo

step "Hold and release a throwaway seat (A3) to show the release path"
HOLD2=$(curl -s -X POST "$BASE/shows/$SHOW_ID/holds" -H "Authorization: Bearer $BUYER1_TOKEN" -H "Content-Type: application/json" \
  -d "{\"seatIds\":[$SEAT_A3]}")
HOLD2_ID=$(echo "$HOLD2" | jget "['holdId']")
curl -s -w "release -> HTTP:%{http_code}\n" -o /dev/null -X DELETE "$BASE/holds/$HOLD2_ID" -H "Authorization: Bearer $BUYER1_TOKEN"
echo "seat A3 released, immediately AVAILABLE again:"
curl -s "$BASE/shows/$SHOW_ID/seats" | python3 -c "import sys,json;d=json.load(sys.stdin);print([s for s in d if s['showSeatId']==$SEAT_A3])"

# =====================================================================================
section "6. Customer: booking with itemized, pro-rata discount breakdown"
# =====================================================================================
step "Create booking on the 2-seat hold, with WELCOME10 applied"
BOOKING=$(curl -s -X POST "$BASE/bookings" -H "Authorization: Bearer $BUYER1_TOKEN" -H "Content-Type: application/json" \
  -d "{\"holdId\":$HOLD_ID,\"discountCode\":\"WELCOME10\"}")
echo "$BOOKING"
BOOKING_ID=$(echo "$BOOKING" | jget "['id']")
BOOKING_REF=$(echo "$BOOKING" | jget "['bookingReference']")

step "Itemized charges: two BASE lines + two pro-rata DISCOUNT lines, summing exactly to the total"
curl -s "$BASE/bookings/$BOOKING_ID/charges" -H "Authorization: Bearer $BUYER1_TOKEN" | python3 -m json.tool

step "Reusing WELCOME10 now fails -> expect 422 DISCOUNT_EXHAUSTED (maxRedemptions was 1)"
step "(fresh hold on A3, since HOLD2_ID above was already released)"
HOLD3=$(curl -s -X POST "$BASE/shows/$SHOW_ID/holds" -H "Authorization: Bearer $BUYER1_TOKEN" -H "Content-Type: application/json" -d "{\"seatIds\":[$SEAT_A3]}")
HOLD3_ID=$(echo "$HOLD3" | jget "['holdId']")
curl -s -w "\nHTTP:%{http_code}\n" -X POST "$BASE/bookings" -H "Authorization: Bearer $BUYER1_TOKEN" -H "Content-Type: application/json" \
  -d "{\"holdId\":$HOLD3_ID,\"discountCode\":\"WELCOME10\"}"
step "release A3's hold so it's free for later sections"
curl -s -o /dev/null -X DELETE "$BASE/holds/$HOLD3_ID" -H "Authorization: Bearer $BUYER1_TOKEN"

# =====================================================================================
section "7. Customer: pay -> confirmed -> notification outbox"
# =====================================================================================
step "Pay for the booking"
curl -s -w "\nHTTP:%{http_code}\n" -X POST "$BASE/bookings/$BOOKING_ID/pay" -H "Authorization: Bearer $BUYER1_TOKEN"

step "Seats now BOOKED"
curl -s "$BASE/shows/$SHOW_ID/seats" | python3 -c "import sys,json;d=json.load(sys.stdin);print([(s['showSeatId'],s['status']) for s in d if s['showSeatId'] in ($SEAT_A1,$SEAT_A2)])"

step "BOOKING_CONFIRMED landed in the outbox — 'trust me, it's async' becomes 'here it is'"
curl -s "$BASE/admin/notifications?bookingRef=$BOOKING_REF" -H "Authorization: Bearer $ADMIN_TOKEN" | python3 -m json.tool

step "Booking status-history timeline"
curl -s "$BASE/bookings/$BOOKING_ID/history" -H "Authorization: Bearer $BUYER1_TOKEN" | python3 -m json.tool

step "Booking list (the other half of 'view booking history')"
curl -s "$BASE/bookings" -H "Authorization: Bearer $BUYER1_TOKEN" | python3 -c "import sys,json;print(json.load(sys.stdin)['totalElements'],'bookings for buyer1')"

# =====================================================================================
section "8. Customer: partial per-seat cancellation with pro-rata refund"
# =====================================================================================
BOOKING_SEATS=$(curl -s "$BASE/bookings/$BOOKING_ID/seats" -H "Authorization: Bearer $BUYER1_TOKEN")
echo "$BOOKING_SEATS" | python3 -m json.tool
FIRST_BOOKING_SEAT_ID=$(echo "$BOOKING_SEATS" | python3 -c "import sys,json;print(json.load(sys.stdin)[0]['id'])")

step "Cancel just ONE of the two booked seats -> PARTIALLY_CANCELLED, pro-rata refund on that seat only"
curl -s -w "\nHTTP:%{http_code}\n" -X POST "$BASE/bookings/$BOOKING_ID/cancel-seats" -H "Authorization: Bearer $BUYER1_TOKEN" -H "Content-Type: application/json" \
  -d "{\"bookingSeatIds\":[$FIRST_BOOKING_SEAT_ID],\"reason\":\"Changed my mind about one seat\"}"

step "The cancelled seat is back to AVAILABLE; the other stays BOOKED"
curl -s "$BASE/shows/$SHOW_ID/seats" | python3 -c "import sys,json;d=json.load(sys.stdin);print([(s['showSeatId'],s['status']) for s in d if s['showSeatId'] in ($SEAT_A1,$SEAT_A2)])"

step "Full status-history now shows PENDING_PAYMENT -> CONFIRMED -> PARTIALLY_CANCELLED"
curl -s "$BASE/bookings/$BOOKING_ID/history" -H "Authorization: Bearer $BUYER1_TOKEN" | python3 -m json.tool

# =====================================================================================
section "9. Payment decline path (never a 500, always a clean 402)"
# =====================================================================================
step "A show priced so the total lands EXACTLY on MockPaymentGateway's forced-failure amount (13.13)"
D_STARTS=$(python3 -c "import datetime;print((datetime.datetime.utcnow()+datetime.timedelta(days=3)).isoformat()+'Z')")
D_ENDS=$(python3 -c "import datetime;print((datetime.datetime.utcnow()+datetime.timedelta(days=3,hours=2)).isoformat()+'Z')")
D_SCREEN_ID=$(curl -s -X POST "$BASE/admin/screens" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d "{\"theaterId\":$THEATER_ID,\"name\":\"Screen Decline Demo\"}" | jget "['id']")
curl -s -X POST "$BASE/admin/screens/$D_SCREEN_ID/seats/bulk" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{"rows":[{"rowLabel":"A","seatCount":1,"category":"REGULAR"}]}' > /dev/null
D_SHOW_ID=$(curl -s -X POST "$BASE/admin/shows" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"screenId\":$D_SCREEN_ID,\"movieId\":$MOVIE_ID,\"startsAt\":\"$D_STARTS\",\"endsAt\":\"$D_ENDS\",\"prices\":[{\"category\":\"REGULAR\",\"baseAmount\":13.13}]}" | jget "['id']")
D_SEAT_ID=$(curl -s "$BASE/shows/$D_SHOW_ID/seats" | python3 -c "import sys,json;print(json.load(sys.stdin)[0]['showSeatId'])")
D_HOLD_ID=$(curl -s -X POST "$BASE/shows/$D_SHOW_ID/holds" -H "Authorization: Bearer $BUYER2_TOKEN" -H "Content-Type: application/json" -d "{\"seatIds\":[$D_SEAT_ID]}" | jget "['holdId']")
D_BOOKING_ID=$(curl -s -X POST "$BASE/bookings" -H "Authorization: Bearer $BUYER2_TOKEN" -H "Content-Type: application/json" -d "{\"holdId\":$D_HOLD_ID}" | jget "['id']")
step "Pay -> expect 402 PAYMENT_FAILED, and the seat stays HELD (not lost — buyer2 can still retry within the hold TTL)"
curl -s -w "\nHTTP:%{http_code}\n" -X POST "$BASE/bookings/$D_BOOKING_ID/pay" -H "Authorization: Bearer $BUYER2_TOKEN"
curl -s "$BASE/shows/$D_SHOW_ID/seats" | python3 -c "import sys,json;print(json.load(sys.stdin)[0]['status'])"

# =====================================================================================
section "10. Admin: seat blocking (a broken seat, a press hold)"
# =====================================================================================
SEAT_D1=$(echo "$SEATS_JSON" | python3 -c "import sys,json;print(json.load(sys.stdin)[3]['showSeatId'])")
step "Block seat, confirm it disappears from bookable availability"
curl -s -w "\nHTTP:%{http_code}\n" -o /dev/null -X POST "$BASE/admin/show-seats/$SEAT_D1/block" -H "Authorization: Bearer $ADMIN_TOKEN"
curl -s "$BASE/shows/$SHOW_ID/seats" | python3 -c "import sys,json;d=json.load(sys.stdin);print([s for s in d if s['showSeatId']==$SEAT_D1])"
step "A hold attempt on the blocked seat -> 409 SEAT_UNAVAILABLE"
curl -s -w "\nHTTP:%{http_code}\n" -X POST "$BASE/shows/$SHOW_ID/holds" -H "Authorization: Bearer $BUYER1_TOKEN" -H "Content-Type: application/json" -d "{\"seatIds\":[$SEAT_D1]}"
step "Unblock it"
curl -s -w "\nHTTP:%{http_code}\n" -o /dev/null -X POST "$BASE/admin/show-seats/$SEAT_D1/unblock" -H "Authorization: Bearer $ADMIN_TOKEN"

# =====================================================================================
section "11. Ops endpoints: force the sweeper, the reminder job, and the notification dispatcher"
# =====================================================================================
curl -s -X POST "$BASE/admin/ops/sweep-now" -H "Authorization: Bearer $ADMIN_TOKEN"; echo
curl -s -X POST "$BASE/admin/ops/remind-now" -H "Authorization: Bearer $ADMIN_TOKEN"; echo
curl -s -X POST "$BASE/admin/notifications/dispatch-now" -H "Authorization: Bearer $ADMIN_TOKEN"; echo
step "Recent notifications across the whole system"
curl -s "$BASE/admin/notifications" -H "Authorization: Bearer $ADMIN_TOKEN" | python3 -m json.tool

# =====================================================================================
section "12. Admin: whole-show cancellation cascades into bulk refund + notification fan-out"
# =====================================================================================
step "Create a throwaway show, book it fully as buyer2, then cancel the SHOW"
C_STARTS=$(python3 -c "import datetime;print((datetime.datetime.utcnow()+datetime.timedelta(days=4)).isoformat()+'Z')")
C_ENDS=$(python3 -c "import datetime;print((datetime.datetime.utcnow()+datetime.timedelta(days=4,hours=2)).isoformat()+'Z')")
C_SCREEN_ID=$(curl -s -X POST "$BASE/admin/screens" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d "{\"theaterId\":$THEATER_ID,\"name\":\"Screen Cascade Demo\"}" | jget "['id']")
curl -s -X POST "$BASE/admin/screens/$C_SCREEN_ID/seats/bulk" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{"rows":[{"rowLabel":"A","seatCount":1,"category":"REGULAR"}]}' > /dev/null
C_SHOW_ID=$(curl -s -X POST "$BASE/admin/shows" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"screenId\":$C_SCREEN_ID,\"movieId\":$MOVIE_ID,\"startsAt\":\"$C_STARTS\",\"endsAt\":\"$C_ENDS\",\"prices\":[{\"category\":\"REGULAR\",\"baseAmount\":300.00}]}" | jget "['id']")
C_SEAT_ID=$(curl -s "$BASE/shows/$C_SHOW_ID/seats" | python3 -c "import sys,json;print(json.load(sys.stdin)[0]['showSeatId'])")
C_HOLD_ID=$(curl -s -X POST "$BASE/shows/$C_SHOW_ID/holds" -H "Authorization: Bearer $BUYER2_TOKEN" -H "Content-Type: application/json" -d "{\"seatIds\":[$C_SEAT_ID]}" | jget "['holdId']")
C_BOOKING_ID=$(curl -s -X POST "$BASE/bookings" -H "Authorization: Bearer $BUYER2_TOKEN" -H "Content-Type: application/json" -d "{\"holdId\":$C_HOLD_ID}" | jget "['id']")
curl -s -X POST "$BASE/bookings/$C_BOOKING_ID/pay" -H "Authorization: Bearer $BUYER2_TOKEN" > /dev/null
echo "booking $C_BOOKING_ID confirmed and paid"

step "Admin cancels the whole show"
curl -s -w "\nHTTP:%{http_code}\n" -o /dev/null -X POST "$BASE/admin/shows/$C_SHOW_ID/cancel" -H "Authorization: Bearer $ADMIN_TOKEN"

step "The booking is now CANCELLED, refunded, with its own history entry"
curl -s "$BASE/bookings/$C_BOOKING_ID" -H "Authorization: Bearer $BUYER2_TOKEN"
echo
curl -s "$BASE/bookings/$C_BOOKING_ID/history" -H "Authorization: Bearer $BUYER2_TOKEN" | python3 -m json.tool

# =====================================================================================
section "13. THE concurrency proof: 20-way contention burst on ONE seat"
# =====================================================================================
step "Register 20 fresh customers"
declare -a TOKENS
for i in $(seq 1 20); do
  T=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
    -d "{\"email\":\"burst$i@demo.local\",\"password\":\"password123\",\"fullName\":\"Burst $i\"}" | jget "['token']")
  TOKENS[$i]="$T"
done

BURST_SEAT_ID=$(curl -s "$BASE/shows/$SHOW_ID/seats" | python3 -c "import sys,json;d=json.load(sys.stdin);print([s for s in d if s['status']=='AVAILABLE'][0]['showSeatId'])")
echo "20 customers now fire POST /shows/$SHOW_ID/holds simultaneously for the SAME seat ($BURST_SEAT_ID)..."

RESULTS_FILE=$(mktemp)
for i in $(seq 1 20); do
  ( curl -s -o /dev/null -w "%{http_code}\n" -X POST "$BASE/shows/$SHOW_ID/holds" \
      -H "Authorization: Bearer ${TOKENS[$i]}" -H "Content-Type: application/json" \
      -d "{\"seatIds\":[$BURST_SEAT_ID]}" >> "$RESULTS_FILE" ) &
done
wait

echo
echo "Result codes:"
sort "$RESULTS_FILE" | uniq -c
WINS=$(grep -c "^201$" "$RESULTS_FILE")
LOSSES=$(grep -c "^409$" "$RESULTS_FILE")
echo
if [ "$WINS" = "1" ] && [ "$LOSSES" = "19" ]; then
  echo "PASS: exactly 1 winner (201), exactly 19 clean losers (409) — no double-allocation, no 500s."
else
  echo "UNEXPECTED: wins=$WINS losses=$LOSSES (expected 1 and 19) — investigate."
fi
rm -f "$RESULTS_FILE"

# =====================================================================================
section "Demo complete"
# =====================================================================================
echo "Covered: catalog admin CRUD, bulk seat layout, show creation + overlap guard, discount"
echo "codes (percentage + flat, usage cap), refund policy scoping, registration/auth, browse,"
echo "seat map, hold/extend/release, booking with pro-rata discount breakdown, payment success"
echo "and decline, partial + full + show-cascade cancellation with pro-rata refunds, seat"
echo "blocking, the transactional outbox + reminder + sweeper ops endpoints, and the 20-way"
echo "no-double-booking concurrency proof."
