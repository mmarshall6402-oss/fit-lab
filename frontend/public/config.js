// Runtime config, loaded before the app bundle so it can be edited in place
// (e.g. directly in the S3 console) without rebuilding the app.
// Set this to the backend's base URL, e.g. "http://fitlab-backend.us-east-1.elasticbeanstalk.com"
// Leave empty ("") to call the API on the same origin the frontend is served from.
window.__FITLAB_API_BASE__ = '';
