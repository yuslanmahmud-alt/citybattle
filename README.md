# CityBattle Plugin - Cara Install

## Keperluan
- Java 17+
- Maven 3.6+
- Spigot atau Paper server 1.20.x

## Cara Build (Compile jadi .jar)

1. Extract zip ni
2. Buka terminal/command prompt dalam folder CityBattle
3. Run command:
   ```
   mvn clean package
   ```
4. File .jar akan keluar dalam folder `target/CityBattle-1.0.0.jar`
5. Copy file tu ke folder `plugins/` server kamu
6. Restart server

---

## Cara Guna

### Setup (Buat sekali je)
1. Pergi ke tengah map bandar kamu
2. `/setcenter` — set pusat border kat situ

### Start Game
1. `/startgame` — semua player auto teleport ke spawn masing-masing dalam kaca, countdown 5,4,3,2,1, kaca hilang, MULA!

### Semasa Game
- `/shrink 200 300` — border mengecil ke 200 blok dalam 5 minit
- `/shrink 80 300` — fasa kedua
- `/shrink 20 120` — final zone
- `/supplydrop` — hantar supply drop kat lokasi random
- `/alive` — tengok berapa player masih hidup

### End/Reset
- `/resetgame` — reset semua untuk round baru

---

## Fasa Border yang Dicadangkan

| Fasa | Command | Masa |
|------|---------|------|
| Start | /startgame | - |
| Fasa 1 | /shrink 200 300 | Minit 5 |
| Fasa 2 | /shrink 80 300 | Minit 10 |
| Fasa 3 | /shrink 30 180 | Minit 15 |
| Final | /shrink 10 60 | Minit 18 |

---

## Feature Plugin
- Auto spawn 10 player tersebar rata dalam bulatan
- Glass cage sekeliling setiap player masa start
- Countdown 5,4,3,2,1 kemudian kaca hilang serentak
- Auto spectator mode bila player mati
- Announce dalam chat siapa eliminate siapa
- Scoreboard tunjuk player hidup dan saiz border
- Supply drop dengan firework dan loot gempak
- Auto detect winner, firework, dan title screen
- Host auto jadi spectator bila /startgame
