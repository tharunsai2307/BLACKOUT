import React, { useEffect, useRef, useState } from 'react';
import maplibregl, { Map as MapLibreMap, Marker, Popup, NavigationControl } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { EmergencyAlert } from '../../types/database';
import { formatCoordinates, formatRelativeTime } from '../../utils/formatters';
import { geoapifyService, GeocodeSearchResult } from '../../services/geoapifyService';
import { Search, MapPin, X, Loader2 } from 'lucide-react';

interface EmergencyMapProps {
  incidents: EmergencyAlert[];
  selectedIncident?: EmergencyAlert | null;
  onSelectIncident?: (incident: EmergencyAlert) => void;
  className?: string;
  enableSearch?: boolean;
}

export const EmergencyMap: React.FC<EmergencyMapProps> = ({
  incidents,
  selectedIncident,
  onSelectIncident,
  className = 'h-full w-full',
  enableSearch = true,
}) => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const markersRef = useRef<Marker[]>([]);
  const searchMarkerRef = useRef<Marker | null>(null);

  // Forward Geocoding Search State
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<GeocodeSearchResult[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [showDropdown, setShowDropdown] = useState(false);

  // Filter incidents with valid GPS coordinates
  const validIncidents = incidents.filter(
    (inc) =>
      inc.latitude != null &&
      inc.longitude != null &&
      !(inc.latitude === 0 && inc.longitude === 0)
  );

  // Default OSM Style Specification
  const osmStyle: maplibregl.StyleSpecification = {
    version: 8,
    sources: {
      'osm-tiles': {
        type: 'raster',
        tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
        tileSize: 256,
        attribution: '&copy; OpenStreetMap contributors | BLACKOUT OPS',
      },
    },
    layers: [
      {
        id: 'osm-tiles-layer',
        type: 'raster',
        source: 'osm-tiles',
        minzoom: 0,
        maxzoom: 19,
      },
    ],
  };

  // Initialize Map
  useEffect(() => {
    if (!mapContainerRef.current || mapRef.current) return;

    // Determine initial style (Geoapify Dark Tactical style if configured, otherwise OSM raster)
    const geoapifyStyleUrl = geoapifyService.getTacticalMapStyleUrl();
    const initialStyle = geoapifyStyleUrl || osmStyle;

    const map = new maplibregl.Map({
      container: mapContainerRef.current,
      style: initialStyle,
      center: [77.2090, 28.6139], // Default center
      zoom: 10,
    });

    // Fallback to OSM if Geoapify style fails
    map.on('error', (e) => {
      if (geoapifyStyleUrl && e.error?.message?.includes('style')) {
        console.warn('Geoapify style failed to load; falling back to OpenStreetMap tiles.');
        map.setStyle(osmStyle);
      }
    });

    map.addControl(new NavigationControl({ showCompass: true }), 'top-right');
    mapRef.current = map;

    return () => {
      map.remove();
      mapRef.current = null;
    };
  }, []);

  // Update Markers
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    // Clear existing markers
    markersRef.current.forEach((m) => m.remove());
    markersRef.current = [];

    if (validIncidents.length === 0) return;

    const bounds = new maplibregl.LngLatBounds();

    validIncidents.forEach((incident) => {
      const lng = incident.longitude!;
      const lat = incident.latitude!;
      bounds.extend([lng, lat]);

      const el = document.createElement('div');
      el.className = 'cursor-pointer group relative';

      const isCritical = incident.priority === 'CRITICAL';
      const isHigh = incident.priority === 'HIGH';
      const markerColor = isCritical ? '#EF4444' : isHigh ? '#F59E0B' : '#06B6D4';

      el.innerHTML = `
        <div style="background-color: ${markerColor};" class="w-6 h-6 rounded-full flex items-center justify-center text-white border-2 border-slate-900 shadow-lg ${
          isCritical ? 'animate-pulse' : ''
        }">
          <span style="font-size: 10px; font-weight: bold; font-family: monospace;">!</span>
        </div>
        ${
          isCritical
            ? `<div style="border-color: ${markerColor};" class="absolute -inset-1 rounded-full border animate-ping pointer-events-none opacity-75"></div>`
            : ''
        }
      `;

      // Popup with incident details
      const popupHtml = `
        <div style="color: #0F172A; font-family: monospace; padding: 4px; font-size: 11px;">
          <div style="font-weight: bold; color: ${markerColor}; margin-bottom: 2px;">
            [${incident.priority}] ${incident.category}
          </div>
          <div style="color: #475569; font-size: 10px; margin-bottom: 4px;">
            ID: ${incident.message_id.slice(0, 8)} • ${formatRelativeTime(incident.created_at)}
          </div>
          <div style="margin-bottom: 4px; max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
            ${incident.description || 'No notes attached'}
          </div>
          <div style="color: #64748B; font-size: 9px; margin-bottom: 6px;">
            ${formatCoordinates(incident.latitude, incident.longitude, incident.accuracy_meters)}
          </div>
          <div style="display: flex; justify-content: space-between; font-size: 9px; border-top: 1px solid #E2E8F0; padding-top: 4px;">
            <span>Hops: ${incident.hop_count}</span>
            <span>Status: <strong>${incident.verification_status}</strong></span>
          </div>
        </div>
      `;

      const popup = new Popup({ offset: 15, closeButton: false }).setHTML(popupHtml);

      const marker = new Marker({ element: el })
        .setLngLat([lng, lat])
        .setPopup(popup)
        .addTo(map);

      el.addEventListener('click', () => {
        if (onSelectIncident) {
          onSelectIncident(incident);
        }
      });

      markersRef.current.push(marker);
    });

    if (!selectedIncident && validIncidents.length > 0) {
      map.fitBounds(bounds, { padding: 60, maxZoom: 14 });
    }
  }, [validIncidents, onSelectIncident]);

  // Fly to selected incident if specified
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !selectedIncident) return;

    if (
      selectedIncident.latitude != null &&
      selectedIncident.longitude != null &&
      !(selectedIncident.latitude === 0 && selectedIncident.longitude === 0)
    ) {
      map.flyTo({
        center: [selectedIncident.longitude, selectedIncident.latitude],
        zoom: 15,
        essential: true,
      });
    }
  }, [selectedIncident]);

  // Handle Forward Geocoding Search
  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!searchQuery.trim()) return;

    try {
      setIsSearching(true);
      const results = await geoapifyService.searchPlaces(searchQuery);
      setSearchResults(results);
      setShowDropdown(true);

      if (results.length > 0) {
        selectSearchResult(results[0]);
      }
    } finally {
      setIsSearching(false);
    }
  };

  const selectSearchResult = (result: GeocodeSearchResult) => {
    const map = mapRef.current;
    if (!map) return;

    setShowDropdown(false);
    setSearchQuery(result.formatted);

    // Remove existing search marker
    if (searchMarkerRef.current) {
      searchMarkerRef.current.remove();
      searchMarkerRef.current = null;
    }

    // Place tactical target search marker
    const el = document.createElement('div');
    el.className = 'w-7 h-7 rounded-full bg-operations-cyan border-2 border-white shadow-xl flex items-center justify-center text-slate-900 font-bold font-mono text-xs animate-bounce';
    el.innerHTML = '⌖';

    const marker = new Marker({ element: el })
      .setLngLat([result.longitude, result.latitude])
      .addTo(map);

    searchMarkerRef.current = marker;

    map.flyTo({
      center: [result.longitude, result.latitude],
      zoom: 14,
      essential: true,
    });
  };

  const clearSearch = () => {
    setSearchQuery('');
    setSearchResults([]);
    setShowDropdown(false);
    if (searchMarkerRef.current) {
      searchMarkerRef.current.remove();
      searchMarkerRef.current = null;
    }
  };

  const noGpsCount = incidents.length - validIncidents.length;

  return (
    <div className={`relative overflow-hidden rounded border border-operations-border bg-operations-surface ${className}`}>
      <div ref={mapContainerRef} className="h-full w-full" />

      {/* Floating Forward Geocoding Search Bar */}
      {enableSearch && (
        <div className="absolute top-3 left-3 z-10 w-72 max-w-[calc(100%-4rem)] font-mono text-xs">
          <form onSubmit={handleSearch} className="relative">
            <input
              type="text"
              placeholder="Search address or landmark..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full bg-operations-card/95 backdrop-blur border border-operations-border rounded pl-8 pr-7 py-1.5 text-white placeholder-slate-400 focus:outline-none focus:border-operations-cyan shadow-lg text-[11px]"
            />
            <Search className="w-3.5 h-3.5 text-operations-muted absolute left-2.5 top-2.5 pointer-events-none" />

            {isSearching ? (
              <Loader2 className="w-3.5 h-3.5 text-operations-cyan animate-spin absolute right-2.5 top-2.5" />
            ) : searchQuery ? (
              <button
                type="button"
                onClick={clearSearch}
                className="absolute right-2.5 top-2.5 text-operations-muted hover:text-white"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            ) : null}
          </form>

          {/* Search Dropdown Results */}
          {showDropdown && searchResults.length > 0 && (
            <div className="mt-1 bg-operations-card border border-operations-border rounded shadow-2xl max-h-48 overflow-y-auto divide-y divide-operations-border/60">
              {searchResults.map((res, idx) => (
                <div
                  key={idx}
                  onClick={() => selectSearchResult(res)}
                  className="p-2 hover:bg-operations-surface cursor-pointer text-[10px] text-slate-200 flex items-start space-x-1.5"
                >
                  <MapPin className="w-3 h-3 text-operations-cyan mt-0.5 flex-shrink-0" />
                  <span className="truncate">{res.formatted}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Floating GPS Coverage Info Badge */}
      <div className="absolute bottom-3 left-3 bg-operations-card/90 backdrop-blur border border-operations-border px-3 py-1.5 rounded text-[11px] font-mono text-operations-muted z-10 flex items-center space-x-3 shadow-lg">
        <span className="text-white font-bold">
          Mapped: <span className="text-operations-cyan">{validIncidents.length}</span> / {incidents.length}
        </span>
        {noGpsCount > 0 && (
          <span className="text-operations-amber">
            ({noGpsCount} alerts lacking GPS fix)
          </span>
        )}
      </div>
    </div>
  );
};
