# TradeSim
Multiplayer Trading Simulationgame

## How it works
* Multiplayer Lobbys with Seasons and ranks
* Realtime marketdata and Portfolio-Updates with WebSocket/STOMP
* JWT-Authentifizierung for Login und Sessionmanagement
* Trading, Portfolio and Leaderboard are in realtime
* React-Frontend with TradingView Lightweight Charts

## Status
Work in progress
Auth, Lobby, Trading, Portfolio and realtimecommunication  
Frontend and backend run on docker

## Repositories
* Backend: [tradesim-backend](https://github.com/poinalex01/tradesim)
* Frontend: [tradesim-frontend](https://github.com/poinalex01/tradesim-frontend)
* Infrastructure: [tradesim-infrastructure](https://github.com/poinalex01/tradesim-infrastructure)

## Tech stack
* Backend: Java 21, Spring Boot (Security, WebSocket/STOMP, JPA), PostgreSQL, JWT
* Frontend: React + Vite, TradingView Lightweight Charts, STOMP/SockJS, Tailwind CSS
* Infrastructure: Docker Compose (Postgres, Backend, Frontend)

## Running
```bash
# in infrastructure
docker compose up --build
