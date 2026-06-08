# Hey Pickler WeChat Mini Program

匹克球赛事活动管理平台 - 微信小程序端

## Project Structure

```
hey-pickler-wxapp/
├── app.js                 # Application entry
├── app.json               # App configuration
├── app.wxss               # Global styles
├── project.config.json    # Project configuration
├── sitemap.json           # Sitemap configuration
├── utils/                 # Utility functions
│   ├── request.js        # HTTP request wrapper with JWT
│   ├── auth.js           # Authentication utilities
│   └── util.js           # Common utilities
├── pages/                 # Pages
│   ├── index/            # Home page
│   ├── login/            # Login page
│   ├── event-detail/     # Event detail page
│   ├── profile/          # User profile page
│   ├── ranking/          # Ranking page
│   └── my-events/        # My events page
├── components/            # Reusable components
│   ├── tier-badge/       # Tier badge component
│   ├── ranking-item/     # Ranking list item
│   └── event-card/       # Event card component
└── images/               # Images and icons
```

## Features

- **WeChat Login**: Secure authentication with WeChat OAuth
- **Event Management**: Browse and register for Star/Party events
- **Ranking System**: View rankings by tier (Legend/Super/Shining)
- **User Profile**: Manage personal info and view points
- **My Events**: Track registered events and status

## API Integration

Base URL: `http://localhost:8080/api/app`

### Key Endpoints

- `POST /auth/login` - WeChat login
- `GET /banners` - Get banners
- `GET /events` - List events (type: STAR/PARTY)
- `GET /events/{id}` - Event detail
- `POST /events/{id}/register` - Register for event
- `POST /events/{id}/cancel` - Cancel registration
- `GET /user/profile` - Get user profile
- `PUT /user/profile` - Update profile
- `GET /rankings` - Get rankings (type, tier)
- `GET /user/events` - Get user's events

## Development

### Prerequisites

- WeChat Developer Tools
- Backend server running at http://localhost:8080

### Setup

1. Open project in WeChat Developer Tools
2. Update `baseUrl` in `app.js` if needed
3. Add appid in `project.config.json`
4. Add tabBar icons in `images/` directory

### Required Icons

Create or add the following icon files (81x81px recommended):

- `images/home.png` - Home icon (inactive)
- `images/home-active.png` - Home icon (active)
- `images/ranking.png` - Ranking icon (inactive)
- `images/ranking-active.png` - Ranking icon (active)
- `images/me.png` - Profile icon (inactive)
- `images/me-active.png` - Profile icon (active)
- `images/default-avatar.png` - Default avatar
- `images/default-event.png` - Default event banner
- `images/logo.png` - App logo

## Design

- **Primary Color**: #4CAF50 (Green)
- **Background**: #f5f5f5
- **Tier Colors**:
  - Legend: Gold gradient (#FFD700 - #FFA500)
  - Super: Purple gradient (#9C27B0 - #7B1FA2)
  - Shining: Silver gradient (#C0C0C0 - #9E9E9E)

## License

© 2026 Hey Pickler
