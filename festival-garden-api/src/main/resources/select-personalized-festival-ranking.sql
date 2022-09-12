SELECT COUNT(performing_artist.spotify_id), festival.*

FROM performing_artist
INNER JOIN user_liked_artist ON performing_artist.spotify_id = user_liked_artist.spotify_artist_id
INNER JOIN festival ON festival.id = performing_artist.festival_id

/* We only want the user's artists which are performing at festival which have not yet happened */
WHERE festival.start_date >= ? AND user_liked_artist.user_id = ?

/* We want to sort the festivals such that those with the most artists in common */
/* with the user are first */
GROUP BY festival.id

ORDER BY COUNT(performing_artist.spotify_id) DESC;